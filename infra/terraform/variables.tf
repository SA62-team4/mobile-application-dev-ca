# Input variables for the Wellness App DigitalOcean infrastructure.
# Set real values in terraform.tfvars (gitignored) — see terraform.tfvars.example.

variable "region" {
  description = "DigitalOcean region slug (e.g. sgp1, nyc3, fra1)."
  type        = string
  default     = "sgp1"
}

variable "droplet_size" {
  description = "Droplet size slug. s-4vcpu-8gb fits the prod mem_limits; g-4vcpu-16gb (restricted on new accounts) gives more Ollama headroom."
  type        = string
  default     = "s-4vcpu-8gb"
}

variable "droplet_image" {
  description = "Base image slug for the droplet."
  type        = string
  default     = "ubuntu-24-04-x64"
}

variable "droplet_name" {
  description = "Name/hostname of the droplet."
  type        = string
  default     = "wellness-prod"
}

variable "project_name" {
  description = "DigitalOcean project that groups these resources."
  type        = string
  default     = "Wellness App"
}

variable "ssh_key_name" {
  description = "Name of an SSH key ALREADY uploaded to your DigitalOcean account. Its public half is also installed for the 'deploy' user via cloud-init."
  type        = string
}

variable "domain" {
  description = "Apex domain managed in DigitalOcean DNS (e.g. example.com). Only used when manage_dns = true."
  type        = string
  default     = ""
}

variable "subdomain" {
  description = "Subdomain for the API host. The FQDN is <subdomain>.<domain>."
  type        = string
  default     = "api"
}

variable "manage_dns" {
  description = "When true, Terraform creates the A record <subdomain>.<domain> -> reserved IP. Set false if DNS is hosted outside DigitalOcean (create the record at your registrar instead)."
  type        = bool
  default     = true
}
