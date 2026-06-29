# Versions + remote state on DO Spaces (S3-compatible).
# bucket/key/endpoints supplied at init: terraform init -backend-config=backend.hcl
# Credentials via AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY (from SPACES_* secrets).

terraform {
  required_version = ">= 1.6.0"

  required_providers {
    digitalocean = {
      source  = "digitalocean/digitalocean"
      version = "~> 2.43"
    }
  }

  backend "s3" {
    # Non-secret flags for a non-AWS S3 endpoint; rest via -backend-config.
    region                      = "us-east-1" # dummy; DO Spaces ignores it
    skip_credentials_validation = true
    skip_metadata_api_check     = true
    skip_region_validation      = true
    skip_requesting_account_id  = true
    skip_s3_checksum            = true
  }
}

# Token read from DIGITALOCEAN_TOKEN env var; never written to code/state.
provider "digitalocean" {}
