provider "aws" {
  region = "ap-southeast-1"
}

module "ai_service_lambda" {
  source                    = "../../modules/ai-service-lambda"
  name                      = "clair-tax-ai-service-staging"
  aws_region                = "ap-southeast-1"
  image_uri                 = "000000000000.dkr.ecr.ap-southeast-1.amazonaws.com/clair-tax-ai-service:staging"
  backend_api_base_url      = "https://backend.staging.clairtax.internal"
  backend_internal_token_arn = "arn:aws:secretsmanager:ap-southeast-1:000000000000:secret:clair-tax/staging/backend-internal-token"
  receipt_bucket_name       = "clair-tax-staging-receipts"
  queue_name                = "clair-tax-staging-receipt-jobs"
}
