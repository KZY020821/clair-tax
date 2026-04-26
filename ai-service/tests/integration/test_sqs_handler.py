"""Integration tests for SQS Lambda handler with moto S3 and partial batch failure."""

import asyncio
import json
from unittest.mock import patch

import pytest

from app.handlers.sqs import lambda_handler
from app.models.extraction import ExtractionResult


def make_sqs_event(records: list[dict]) -> dict:
    return {"Records": records}


def make_record(message_id: str, body: dict | str) -> dict:
    body_str = json.dumps(body) if isinstance(body, dict) else body
    return {
        "messageId": message_id,
        "body": body_str,
        "receiptHandle": f"handle-{message_id}",
        "attributes": {},
        "messageAttributes": {},
        "md5OfBody": "",
        "eventSource": "aws:sqs",
        "eventSourceARN": "arn:aws:sqs:us-east-1:123456789:test-queue",
        "awsRegion": "us-east-1",
    }


def valid_job_body(receipt_id: str = "r-001", s3_key: str = "receipts/test.jpg") -> dict:
    return {
        "receipt_id": receipt_id,
        "s3_key": s3_key,
        "user_id": "user-1",
        "currency": "MYR",
    }


@pytest.fixture
def fake_extraction_result():
    return ExtractionResult(
        receipt_id="r-001",
        extraction_status="extracted",
        amount="32.54",
        currency="MYR",
        date="2024-03-15",
        merchant_name="MYDIN",
    )


class TestLambdaHandlerBasic:
    def test_successful_single_record(self, mock_s3, mock_backend_api, fake_extraction_result):
        event = make_sqs_event([make_record("msg-1", valid_job_body("r-001"))])
        with patch(
            "app.handlers.sqs.process_receipt",
            return_value=fake_extraction_result,
        ) as mock_proc:
            result = lambda_handler(event, None)

        assert result["batchItemFailures"] == []
        mock_proc.assert_called_once()

    def test_returns_dict_with_batch_item_failures_key(self, fake_extraction_result):
        event = make_sqs_event([make_record("msg-1", valid_job_body())])
        with patch("app.handlers.sqs.process_receipt", return_value=fake_extraction_result):
            result = lambda_handler(event, None)
        assert "batchItemFailures" in result

    def test_empty_records_returns_empty_failures(self):
        event = make_sqs_event([])
        result = lambda_handler(event, None)
        assert result["batchItemFailures"] == []


class TestMalformedRecords:
    def test_invalid_json_body_goes_to_failures(self):
        event = make_sqs_event([make_record("msg-bad-json", "not valid json {{{")])
        result = lambda_handler(event, None)
        assert any(f["itemIdentifier"] == "msg-bad-json" for f in result["batchItemFailures"])

    def test_missing_required_fields_goes_to_failures(self):
        body = {"receipt_id": "r-no-s3-key"}  # missing s3_key and user_id
        event = make_sqs_event([make_record("msg-missing-fields", body)])
        result = lambda_handler(event, None)
        assert any(f["itemIdentifier"] == "msg-missing-fields" for f in result["batchItemFailures"])

    def test_invalid_s3_key_prefix_goes_to_failures(self):
        body = {"receipt_id": "r-bad", "s3_key": "wrong/path/file.jpg", "user_id": "u-1"}
        event = make_sqs_event([make_record("msg-bad-s3", body)])
        result = lambda_handler(event, None)
        assert any(f["itemIdentifier"] == "msg-bad-s3" for f in result["batchItemFailures"])


class TestPartialBatchFailure:
    def test_good_records_succeed_bad_records_fail(self, fake_extraction_result):
        """Partial batch: good record succeeds, bad JSON record fails."""
        records = [
            make_record("msg-good", valid_job_body("r-good")),
            make_record("msg-bad", "not json"),
        ]
        event = make_sqs_event(records)

        with patch("app.handlers.sqs.process_receipt", return_value=fake_extraction_result):
            result = lambda_handler(event, None)

        failures = [f["itemIdentifier"] for f in result["batchItemFailures"]]
        assert "msg-bad" in failures
        assert "msg-good" not in failures

    def test_storage_error_adds_to_failures(self):
        from app.clients.s3_storage import StorageError

        event = make_sqs_event([make_record("msg-s3-fail", valid_job_body("r-s3"))])

        with patch(
            "app.handlers.sqs.process_receipt",
            side_effect=StorageError(
                s3_key="receipts/test.jpg",
                operation="get_object",
                cause=Exception("NoSuchKey"),
                not_found=True,
            ),
        ):
            result = lambda_handler(event, None)

        assert any(f["itemIdentifier"] == "msg-s3-fail" for f in result["batchItemFailures"])

    def test_backend_api_error_adds_to_failures(self):
        from app.clients.backend_api import BackendApiError

        event = make_sqs_event([make_record("msg-backend-fail", valid_job_body("r-backend"))])

        with patch(
            "app.handlers.sqs.process_receipt",
            side_effect=BackendApiError("5xx from Spring Boot"),
        ):
            result = lambda_handler(event, None)

        assert any(f["itemIdentifier"] == "msg-backend-fail" for f in result["batchItemFailures"])

    def test_unexpected_exception_adds_to_failures(self):
        event = make_sqs_event([make_record("msg-unexpected", valid_job_body("r-unexpected"))])

        with patch(
            "app.handlers.sqs.process_receipt",
            side_effect=RuntimeError("completely unexpected"),
        ):
            result = lambda_handler(event, None)

        assert any(f["itemIdentifier"] == "msg-unexpected" for f in result["batchItemFailures"])

    def test_multiple_records_all_succeed(self, fake_extraction_result):
        records = [
            make_record(f"msg-{i}", valid_job_body(f"r-{i}", f"receipts/file-{i}.jpg"))
            for i in range(5)
        ]
        event = make_sqs_event(records)

        with patch("app.handlers.sqs.process_receipt", return_value=fake_extraction_result):
            result = lambda_handler(event, None)

        assert result["batchItemFailures"] == []

    def test_multiple_records_all_fail(self):
        records = [
            make_record(f"msg-{i}", "invalid json {{{")
            for i in range(3)
        ]
        event = make_sqs_event(records)
        result = lambda_handler(event, None)
        assert len(result["batchItemFailures"]) == 3

    def test_mixed_batch_correct_failure_count(self, fake_extraction_result):
        records = [
            make_record("msg-ok-1", valid_job_body("r-ok-1")),
            make_record("msg-fail-1", "bad json"),
            make_record("msg-ok-2", valid_job_body("r-ok-2")),
            make_record("msg-fail-2", "also bad"),
        ]
        event = make_sqs_event(records)

        with patch("app.handlers.sqs.process_receipt", return_value=fake_extraction_result):
            result = lambda_handler(event, None)

        failures = [f["itemIdentifier"] for f in result["batchItemFailures"]]
        assert len(failures) == 2
        assert "msg-fail-1" in failures
        assert "msg-fail-2" in failures
        assert "msg-ok-1" not in failures
        assert "msg-ok-2" not in failures
