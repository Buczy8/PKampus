# ==============================================================================
# PKampus prod infra: Oracle Always Free (Terraform)
# Single VM.Standard.A1.Flex (2 OCPU / 12 GB) + own VCN + reserved public IP.
# Backend runs here via docker-compose.prod.yml (Ansible, infra/ansible/).
# DB = Supabase, frontend = Vercel, mail = Resend — not managed here.
# ==============================================================================

terraform {
  required_version = ">= 1.6"

  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "~> 6.0"
    }
  }
}
