locals {
  name = "pkampus-prod"
}

resource "azurerm_resource_group" "pkampus" {
  name     = "${local.name}-rg"
  location = var.location
}

resource "azurerm_virtual_network" "pkampus" {
  name                = "${local.name}-vnet"
  resource_group_name = azurerm_resource_group.pkampus.name
  location            = azurerm_resource_group.pkampus.location
  address_space       = ["10.1.0.0/16"]
}

resource "azurerm_subnet" "pkampus" {
  name                 = "${local.name}-subnet"
  resource_group_name  = azurerm_resource_group.pkampus.name
  virtual_network_name = azurerm_virtual_network.pkampus.name
  address_prefixes     = ["10.1.1.0/24"]
}

# Firewall: SSH + Caddy HTTP/HTTPS in, all out. (No on-host UFW in Ansible.)
resource "azurerm_network_security_group" "pkampus" {
  name                = "${local.name}-nsg"
  resource_group_name = azurerm_resource_group.pkampus.name
  location            = azurerm_resource_group.pkampus.location

  security_rule {
    name                       = "ssh"
    priority                   = 100
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "22"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }

  security_rule {
    name                       = "http"
    priority                   = 110
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "80"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }

  security_rule {
    name                       = "https"
    priority                   = 120
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "443"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }
}

resource "azurerm_subnet_network_security_group_association" "pkampus" {
  subnet_id                 = azurerm_subnet.pkampus.id
  network_security_group_id = azurerm_network_security_group.pkampus.id
}

# Static IP (~$3.65/mo): survives stop/start, DNS-safe. Never use Dynamic
# (changes on stop/start and rots the A record).
resource "azurerm_public_ip" "api" {
  name                = "${local.name}-ip"
  resource_group_name = azurerm_resource_group.pkampus.name
  location            = azurerm_resource_group.pkampus.location
  allocation_method   = "Static"
  sku                 = "Standard"
}

resource "azurerm_network_interface" "api" {
  name                = "${local.name}-nic"
  resource_group_name = azurerm_resource_group.pkampus.name
  location            = azurerm_resource_group.pkampus.location

  ip_configuration {
    name                          = "internal"
    subnet_id                     = azurerm_subnet.pkampus.id
    private_ip_address_allocation = "Dynamic"
    public_ip_address_id          = azurerm_public_ip.api.id
  }
}

resource "azurerm_linux_virtual_machine" "api" {
  name                = "${local.name}-api"
  resource_group_name = azurerm_resource_group.pkampus.name
  location            = azurerm_resource_group.pkampus.location
  size                = var.vm_size
  admin_username      = var.admin_username

  disable_password_authentication = true

  network_interface_ids = [
    azurerm_network_interface.api.id,
  ]

  admin_ssh_key {
    username   = var.admin_username
    public_key = file(var.ssh_public_key_path)
  }

  os_disk {
    caching              = "ReadWrite"
    storage_account_type = "Standard_LRS"
    disk_size_gb         = 32
  }

  source_image_reference {
    publisher = "Canonical"
    offer     = "ubuntu-24_04-lts"
    sku       = "server"
    version   = "latest"
  }
}
