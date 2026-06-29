# DigitalOcean infra only — no app secrets (deploy workflow handles those).

# Existing DO SSH key; its public half is reused for the droplet 'deploy' user.
data "digitalocean_ssh_key" "deploy" {
  name = var.ssh_key_name
}

# ── Droplet ────────────────────────────────────────────────────────────────
resource "digitalocean_droplet" "wellness" {
  name       = var.droplet_name
  region     = var.region
  size       = var.droplet_size
  image      = var.droplet_image
  ssh_keys   = [data.digitalocean_ssh_key.deploy.id]
  monitoring = true
  tags       = ["wellness", "prod"]

  # Minimal bootstrap: creates 'deploy' user + installs Python so Ansible can
  # take over Docker install and app config. No secrets.
  user_data = templatefile("${path.module}/cloud-init.yaml", {
    ssh_public_key = data.digitalocean_ssh_key.deploy.public_key
  })
}

# ── Reserved IP ──────────────────────────────────────────────────────────────
# Stable public address (DNS + SSH deploy target it).
resource "digitalocean_reserved_ip" "wellness" {
  region     = var.region
  droplet_id = digitalocean_droplet.wellness.id
}

# ── Cloud firewall ───────────────────────────────────────────────────────────
# Only 22/80/443 inbound; data/AI services stay on the internal Docker network.
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

  # Allow all outbound (apt, model/image pulls, ACME).
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

# ── Project grouping ─────────────────────────────────────────────────────────
resource "digitalocean_project" "wellness" {
  name        = var.project_name
  description = "AI-enabled wellness app — production droplet and networking."
  purpose     = "Web Application"
  environment = "Production"
  resources = [
    digitalocean_droplet.wellness.urn,
    digitalocean_reserved_ip.wellness.urn,
  ]
}

# ── DNS (optional) ───────────────────────────────────────────────────────────
# <subdomain>.<domain> -> reserved IP. Disable if DNS is hosted outside DO.
resource "digitalocean_record" "api" {
  count  = var.manage_dns ? 1 : 0
  domain = var.domain
  type   = "A"
  name   = var.subdomain
  value  = digitalocean_reserved_ip.wellness.ip_address
  ttl    = 300
}
