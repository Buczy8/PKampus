variable "subscription_id" {
  description = "Azure subscription ID (student subscription with the $100 credit)."
  type        = string
}

variable "location" {
  description = "Same price tier across EU; Poland Central = lowest ping from PL."
  type        = string
  default     = "polandcentral"
}

variable "vm_size" {
  description = "B2ts_v2 = 2 vCPU/1 GB for the price of B1s. B1ls (0.5 GB) cannot fit Spring Boot — see README."
  type        = string
  default     = "Standard_B2ts_v2"
}

variable "admin_username" {
  description = "Must stay 'ubuntu': infra/ansible paths assume /home/ubuntu."
  type        = string
  default     = "ubuntu"
}

variable "ssh_public_key_path" {
  description = "Local path to the SSH PUBLIC key (same pkampus-deploy.pub as elsewhere)."
  type        = string
}

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
