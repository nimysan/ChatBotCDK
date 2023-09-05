#!/bin/bash

sudo dnf install git postgresql15 pip docker -y
sudo systemctl start docker
sudo usermod -aG docker $USER && newgrp docker

# start pgAdmin4
docker run -p 80:80 \
    -e "PGADMIN_DEFAULT_EMAIL=yexw@amazon.com" \
    -e "PGADMIN_DEFAULT_PASSWORD=SuperSecret" \
    -d dpage/pgadmin4

#deploy ChatBotWebUI
git clone https://github.com/nimysan/ChatBotWebUI.git && cd  ChatBotWebUI &&chmod a+x ./deploy.sh &&  ./deploy.sh