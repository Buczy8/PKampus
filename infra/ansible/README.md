# infra/ansible — provision + deploy the API host (free, agentless over SSH)

Idempotent playbook: Docker, repo pull, `/etc/pkampus/.env` (0600),
`docker-compose.prod.yml up --build`, then gates on
`https://<api_host>/actuator/health`. Re-running = deploying latest master.

## 0. Install Ansible (laptop, once)

```bash
pipx install ansible-core   # or: pip install --user ansible-core
```

## 1. Configure (never commit secrets)

```bash
cd infra/ansible
cp vars/prod.yml.example vars/prod.yml && chmod 600 vars/prod.yml
nano vars/prod.yml        # real Supabase/Resend/MinIO/JWT values
nano inventory.ini        # RESERVED_IP_FROM_TERRAFORM
# optional: ansible-vault encrypt vars/prod.yml  (run with --ask-vault-pass)
```

## 2. Run (AFTER terraform apply + DNS A record propagated)

```bash
ansible-playbook -i inventory.ini playbook.yml
# with vault: ansible-playbook -i inventory.ini playbook.yml --ask-vault-pass
```

## 3. Day-to-day

- Normal deploys go through the GitHub `deploy-backend` workflow (push to
  master). Run this playbook when the host itself changes (Docker, env keys,
  Caddyfile) or to recover a fresh VM.
- Firewall lives in the OCI security list (22/80/443); no on-host UFW here.
