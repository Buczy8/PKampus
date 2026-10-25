output "public_ip" {
  description = "Point the DNS A record at this IP (static, survives stop/start)."
  value       = azurerm_public_ip.api.ip_address
}

output "api_url" {
  description = "Public API origin after DNS + Caddy are up."
  value       = "https://${var.api_subdomain}.${var.domain}"
}

output "ssh" {
  description = "First login (key pkampus-deploy, no password)."
  value       = "ssh ${var.admin_username}@${azurerm_public_ip.api.ip_address}"
}

output "next_dns_records" {
  description = "Records to create at the domain registrar before running Ansible."
  value       = <<-EOT
    A     ${var.api_subdomain}  ->  ${azurerm_public_ip.api.ip_address}   (backend, Caddy TLS)
    (Resend SPF/DKIM/DMARC records come from the Resend Domains panel.)
  EOT
}
