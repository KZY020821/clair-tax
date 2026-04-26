from pydantic import BaseModel, ConfigDict, model_validator


class ReceiptJob(BaseModel):
    model_config = ConfigDict(strict=True)

    receipt_id: str
    s3_key: str
    user_id: str
    currency: str = "MYR"

    @model_validator(mode="after")
    def validate_s3_key(self) -> "ReceiptJob":
        if not self.s3_key.startswith("receipts/"):
            raise ValueError(f"s3_key must start with 'receipts/', got: {self.s3_key!r}")
        return self
