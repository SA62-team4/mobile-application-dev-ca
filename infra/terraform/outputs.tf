# Outputs consumed by operators and the deploy workflow.

output "reserved_ip" {
  description = "Stable public IP of the droplet. Point DNS / SSH deploy at this."
  value       = digitalocean_reserved_ip.wellness.ip_address
}

output "droplet_id" {
  description = "DigitalOcean droplet ID."
  value       = digitalocean_droplet.wellness.id
}

output "droplet_ipv4" {
  description = "Droplet's own public IPv4 (usually prefer reserved_ip)."
  value       = digitalocean_droplet.wellness.ipv4_address
}

output "fqdn" {
  description = "API hostname. Empty when manage_dns = false."
  value       = var.manage_dns ? "${var.subdomain}.${var.domain}" : ""
}
