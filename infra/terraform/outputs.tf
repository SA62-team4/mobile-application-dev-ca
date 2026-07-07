# @author Tiong Zhong Cheng
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

output "sonar_reserved_ip" {
  description = "Stable public IP of the SonarQube droplet. Use for SONAR_DROPLET_HOST."
  value       = digitalocean_reserved_ip.sonar.ip_address
}

output "sonar_droplet_id" {
  description = "DigitalOcean SonarQube droplet ID."
  value       = digitalocean_droplet.sonar.id
}

output "sonar_droplet_ipv4" {
  description = "SonarQube droplet's own public IPv4. Usually prefer sonar_reserved_ip."
  value       = digitalocean_droplet.sonar.ipv4_address
}

output "sonar_fqdn" {
  description = "SonarQube dashboard hostname. Empty when manage_dns = false."
  value       = var.manage_dns ? "${var.sonar_subdomain}.${var.domain}" : ""
}
