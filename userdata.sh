#!/bin/bash

sudo dnf install git postgresql15 pip docker -y && sudo systemctl start docker && sudo usermod -aG docker $USER && newgrp docker && sudo systemctl start docker
# start pgAdmin4
docker run -p 80:80 \
    -e "PGADMIN_DEFAULT_EMAIL=${pgAdmin4UserName}" \
    -e "PGADMIN_DEFAULT_PASSWORD=${pgAdmin4Password}" \
    -d dpage/pgadmin4

#deploy ChatBotWebUI
git clone https://github.com/nimysan/ChatBotWebUI.git
cd  ChatBotWebUI

#初始化配置
cat << EOF > config.json
{
  "pg_config": [
    "${pg_host}",
    "${pg_port}",
    "${pg_database}",
    "${pg_username}",
    "${pg_password}"
  ],
  "openai_key": "${openai_key}"
}
EOF

chmod a+x ./deploy.sh
./deploy.sh