# Un module doit déclarer les providers hors « hashicorp/* » (sinon Terraform cherche hashicorp/kind).
terraform {
  required_providers {
    kind = {
      source  = "tehcyx/kind"
      version = "~> 0.9"
    }
  }
}
