# ==============================================================================
# PKampus prod infra: Microsoft Azure (student credit, ~$15/mo 24/7)
# Single Standard_B2ts_v2 (2 vCPU / 1 GB) + VNet + NSG (22/80/443) + static IP.
# Backend runs here via docker-compose.prod.yml (Ansible, infra/ansible/ —
# cloud-agnostic, only inventory.ini changes).
# DB = Supabase, frontend = Vercel, mail = Resend — not managed here.
# ==============================================================================

terraform {
  required_version = ">= 1.6"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
  }
}

provider "azurerm" {
  subscription_id = var.subscription_id
  features {}
}
