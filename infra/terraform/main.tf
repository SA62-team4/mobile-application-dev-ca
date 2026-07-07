# @author Tiong Zhong Cheng
# DigitalOcean infra only. App secrets come from the deploy workflow.

# Existing DO SSH key.
data "digitalocean_ssh_key" "deploy" {
  name = var.ssh_key_name
}

# Droplets.
resource "digitalocean_droplet" "wellness" {
  name       = var.droplet_name
  region     = var.region
  size       = var.droplet_size
  image      = var.droplet_image
  ssh_keys   = [data.digitalocean_ssh_key.deploy.id]
  monitoring = true
  tags       = ["wellness", "prod"]

  # Minimal bootstrap for Ansible.
  user_data = templatefile("${path.module}/cloud-init.yaml", {
    ssh_public_key = data.digitalocean_ssh_key.deploy.public_key
  })
}

resource "digitalocean_droplet" "sonar" {
  name       = var.sonar_droplet_name
  region     = var.region
  size       = var.sonar_droplet_size
  image      = var.droplet_image
  ssh_keys   = [data.digitalocean_ssh_key.deploy.id]
  monitoring = true
  tags       = ["wellness", "sonarqube", "quality"]

  # Minimal bootstrap for Ansible.
  user_data = templatefile("${path.module}/cloud-init.yaml", {
    ssh_public_key = data.digitalocean_ssh_key.deploy.public_key
  })
}

# Reserved IPs.
resource "digitalocean_reserved_ip" "wellness" {
  region     = var.region
  droplet_id = digitalocean_droplet.wellness.id
}

resource "digitalocean_reserved_ip" "sonar" {
  region     = var.region
  droplet_id = digitalocean_droplet.sonar.id
}

# Cloud firewall.
resource "digitalocean_firewall" "wellness" {
  name        = "wellness-prod-fw"
  droplet_ids = [digitalocean_droplet.wellness.id]

  inbound_rule {
    protocol         = "tcp"
    port_range       = "22"
    source_addresses = ["0.0.0.0/0", "::/0"]
  }
  inbound_rule {
    protocol         = "tcp"
    port_range       = "80"
    source_addresses = ["0.0.0.0/0", "::/0"]
  }
  inbound_rule {
    protocol         = "tcp"
    port_range       = "443"
    source_addresses = ["0.0.0.0/0", "::/0"]
  }

  # Allow outbound package, image, model, and ACME traffic.
  outbound_rule {
    protocol              = "tcp"
    port_range            = "1-65535"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }
  outbound_rule {
    protocol              = "udp"
    port_range            = "1-65535"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }
  outbound_rule {
    protocol              = "icmp"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }
}

resource "digitalocean_firewall" "sonar" {
  name        = "wellness-sonar-fw"
  droplet_ids = [digitalocean_droplet.sonar.id]

  inbound_rule {
    protocol         = "tcp"
    port_range       = "22"
    source_addresses = ["0.0.0.0/0", "::/0"]
  }
  inbound_rule {
    protocol         = "tcp"
    port_range       = "80"
    source_addresses = ["0.0.0.0/0", "::/0"]
  }
  inbound_rule {
    protocol         = "tcp"
    port_range       = "443"
    source_addresses = ["0.0.0.0/0", "::/0"]
  }

  # Allow outbound for apt, Docker image pulls, ACME, and SonarQube updates.
  outbound_rule {
    protocol              = "tcp"
    port_range            = "1-65535"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }
  outbound_rule {
    protocol              = "udp"
    port_range            = "1-65535"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }
  outbound_rule {
    protocol              = "icmp"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }
}

# Project grouping.
resource "digitalocean_project" "wellness" {
  name        = var.project_name
  description = "AI-enabled wellness app - production and quality dashboard infrastructure."
  purpose     = "Web Application"
  environment = "Production"
  resources = [
    digitalocean_droplet.wellness.urn,
    digitalocean_reserved_ip.wellness.urn,
    digitalocean_droplet.sonar.urn,
    digitalocean_reserved_ip.sonar.urn,
  ]
}

# Optional DNS.
# <subdomain>.<domain> -> reserved IP.
resource "digitalocean_record" "api" {
  count  = var.manage_dns ? 1 : 0
  domain = var.domain
  type   = "A"
  name   = var.subdomain
  value  = digitalocean_reserved_ip.wellness.ip_address
  ttl    = 300
}

# sonar.<domain> -> SonarQube reserved IP.
resource "digitalocean_record" "sonar" {
  count  = var.manage_dns ? 1 : 0
  domain = var.domain
  type   = "A"
  name   = var.sonar_subdomain
  value  = digitalocean_reserved_ip.sonar.ip_address
  ttl    = 300
}
