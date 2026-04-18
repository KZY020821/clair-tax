terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

resource "aws_sqs_queue" "receipt_dlq" {
  name = "${var.queue_name}-dlq"
}

resource "aws_sqs_queue" "receipt_jobs" {
  name = var.queue_name

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.receipt_dlq.arn
    maxReceiveCount     = 5
  })
}

resource "aws_iam_role" "lambda_role" {
  name = "${var.name}-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy" "lambda_policy" {
  name = "${var.name}-lambda-policy"
  role = aws_iam_role.lambda_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:HeadObject"
        ]
        Resource = "arn:aws:s3:::${var.receipt_bucket_name}/*"
      },
      {
        Effect = "Allow"
        Action = [
          "textract:AnalyzeExpense"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue"
        ]
        Resource = var.backend_internal_token_arn
      },
      {
        Effect = "Allow"
        Action = [
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes"
        ]
        Resource = aws_sqs_queue.receipt_jobs.arn
      }
    ]
  })
}

resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/${var.name}"
  retention_in_days = 14
}

resource "aws_lambda_function" "ai_service" {
  function_name = var.name
  package_type  = "Image"
  image_uri     = var.image_uri
  role          = aws_iam_role.lambda_role.arn
  timeout       = 60
  memory_size   = 1024

  environment {
    variables = {
      AWS_REGION            = var.aws_region
      BACKEND_API_BASE_URL  = var.backend_api_base_url
      BACKEND_INTERNAL_TOKEN_SECRET_ARN = var.backend_internal_token_arn
      DEFAULT_RECEIPT_CURRENCY = "MYR"
    }
  }
}

resource "aws_lambda_event_source_mapping" "receipt_jobs" {
  event_source_arn = aws_sqs_queue.receipt_jobs.arn
  function_name    = aws_lambda_function.ai_service.arn
  batch_size       = 10
}

resource "aws_cloudwatch_metric_alarm" "dlq_depth" {
  alarm_name          = "${var.name}-receipt-dlq-depth"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 300
  statistic           = "Maximum"
  threshold           = 0

  dimensions = {
    QueueName = aws_sqs_queue.receipt_dlq.name
  }
}
