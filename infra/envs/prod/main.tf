provider "aws" {
  region = "ap-southeast-1"
}

module "ai_service_lambda" {
  source                    = "../../modules/ai-service-lambda"
  name                      = "clair-tax-ai-service-prod"
  aws_region                = "ap-southeast-1"
  image_uri                 = "000000000000.dkr.ecr.ap-southeast-1.amazonaws.com/clair-tax-ai-service:prod"
  backend_api_base_url      = "https://backend.clairtax.internal"
  backend_internal_token_arn = "arn:aws:secretsmanager:ap-southeast-1:000000000000:secret:clair-tax/prod/backend-internal-token"
  receipt_bucket_name       = "clair-tax-prod-receipts"
  queue_name                = "clair-tax-prod-receipt-jobs"
}
