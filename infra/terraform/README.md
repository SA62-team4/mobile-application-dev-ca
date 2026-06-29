# Terraform — Wellness App (DigitalOcean)

Provisions a single hardened droplet (Docker stack host), reserved IP, cloud
firewall (22/80/443), DNS, and project. Infra only — app deploy and secrets are
handled by the GitHub Actions `deploy.yml` workflow.

For CI, these are stored as GitHub Actions secrets/variables on the `production`
Environment — see the table in `docs/specs/10-plan-docker-devops.md`.

## Prerequisites
- A DO API token (`export DIGITALOCEAN_TOKEN=...`).
- A DO Space for state, with access keys
  (`export AWS_ACCESS_KEY_ID=... AWS_SECRET_ACCESS_KEY=...`).
- An SSH key already uploaded to the DO account (its name → `ssh_key_name`).

## SSH key setup (simple)

Terraform does not create an SSH key — it reuses one already in your DO account.
The same public key is installed for the droplet's `deploy` user (used by the
deploy workflow).

1. Generate a key (skip if you have one):

   ```bash
   ssh-keygen -t ed25519 -C "wellness-deploy" -f ~/.ssh/wellness
   ```

2. Add the **public** key to DigitalOcean: Settings → Security → SSH Keys → Add,
   paste `~/.ssh/wellness.pub`, and give it a name.

3. Put that name in `terraform.tfvars` as `ssh_key_name`.

4. For CI, store the **private** key (`~/.ssh/wellness`) as the `DEPLOY_SSH_KEY`
   GitHub secret so `deploy.yml` can SSH in.

## Variables

Set in `terraform.tfvars` (see `terraform.tfvars.example`).

| Variable | Required | Default | Description |
| --- | --- | --- | --- |
| `ssh_key_name` | yes | — | Name of an SSH key already in your DO account |
| `region` | no | `sgp1` | DO region slug |
| `droplet_size` | no | `s-4vcpu-8gb` | Size slug. 8 GB fits the prod mem_limits; `g-4vcpu-16gb` is roomier but tier-restricted on new accounts |
| `droplet_image` | no | `ubuntu-24-04-x64` | Base image slug |
| `droplet_name` | no | `wellness-prod` | Droplet hostname |
| `project_name` | no | `Wellness App` | DO project that groups resources |
| `manage_dns` | no | `true` | Create the A record in DO DNS. Set `false` for a registrar or DuckDNS |
| `domain` | when `manage_dns` | `""` | Apex domain in DO DNS, e.g. `example.com` |
| `subdomain` | no | `api` | API host; FQDN is `<subdomain>.<domain>` |

### No domain? Use DuckDNS (free, keeps HTTPS)

DO does not give out domains. For a free hostname with valid TLS:

1. Create a subdomain at [duckdns.org](https://www.duckdns.org), e.g.
   `sa62wellness` → `sa62wellness.duckdns.org`.
2. `terraform apply`, then read the reserved IP from the workflow output or run
   `terraform output reserved_ip`.
3. Point that DuckDNS subdomain at the **reserved IP** and save. Do not use the
   Droplet's temporary IPv4 if it differs from `reserved_ip`.
4. Verify DNS before deploying:

   ```bash
   dig +short sa62wellness.duckdns.org
   ```

   The command must print the reserved IP.
5. Set `manage_dns = false` (leave `domain`/`subdomain` unused) and the GitHub
   Variable `API_DOMAIN = sa62wellness.duckdns.org`.

Caddy issues the cert once the name resolves.

## DO Space for remote state (recommended config)

- **Region:** same as the droplet (e.g. `sgp1`) for low latency.
- **Name:** globally unique, dedicated to state, e.g. `sa62-wellness-tfstate`.
- **Access:** keep the Space **Private** (never public). State holds resource
  metadata.
- **Keys:** generate a **Spaces access key** (API → Spaces Keys), separate from the
  API token → store as `SPACES_ACCESS_KEY` / `SPACES_SECRET_KEY` secrets.
- **Endpoint:** `https://<region>.digitaloceanspaces.com`; state key
  `wellness/terraform.tfstate`.
- **Locking:** DO Spaces has no DynamoDB-style lock. Run infra changes serially
  (the `infra.yml` workflow is manual, single-runner) to avoid state races.
- **Note:** Spaces lacks S3 object versioning — keep state changes deliberate and
  consider a periodic copy of the state object as a backup.

## Usage
```bash
cp terraform.tfvars.example terraform.tfvars   # edit
cp backend.hcl.example backend.hcl             # edit
terraform init -backend-config=backend.hcl
terraform plan
terraform apply
terraform output            # reserved_ip, fqdn
```

cloud-init only creates the `deploy` user and installs Python. After apply, the
deploy workflow runs the Ansible playbooks in `infra/ansible/`, which install
Docker, ship app files, write `.env` from GitHub secrets, and start the prod
stack. `terraform destroy` tears it all down.
