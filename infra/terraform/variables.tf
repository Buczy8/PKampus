# --- OCI API auth (never commit values; pass via TF_VAR_* or terraform.tfvars) ---
variable "tenancy_ocid" {
  description = "Tenancy OCID (OCI Console -> Governance -> Tenancy Details)."
  type        = string
}

variable "user_ocid" {
  description = "User OCID (Identity -> Users -> your user). API key must be added here."
  type        = string
}

variable "fingerprint" {
  description = "Fingerprint of the API key added to the user above."
  type        = string
}

variable "private_key_path" {
  description = "Local path to the API private key (.pem). chmod 600, never commit."
  type        = string
}

variable "region" {
  description = "Home region, e.g. eu-frankfurt-1. Always Free A1 lives in the home region."
  type        = string
  default     = "eu-frankfurt-1"
}

variable "compartment_ocid" {
  description = "Compartment for prod resources. Empty = tenancy root (fine for a thesis)."
  type        = string
  default     = ""
}

# --- Instance ---
variable "ssh_public_key_path" {
  description = "Local path to the SSH PUBLIC key injected into the VM (ubuntu user)."
  type        = string
}

variable "boot_volume_gb" {
  description = "Boot volume size. Always Free pool is 200 GB total; 100 leaves room."
  type        = number
  default     = 100
}

# --- Naming (outputs only; DNS records are created at the registrar) ---
variable "domain" {
  description = "Own domain, e.g. pkampus.me (GitHub Student Pack)."
  type        = string
  default     = "pkampus.me"
}

variable "api_subdomain" {
  description = "Backend hostname: https://<api_subdomain>.<domain>/api/v1."
  type        = string
  default     = "api"
}
