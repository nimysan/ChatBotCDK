#!/bin/bash
dnf update -y
dnf install git postgresql15 pip docker -y

#enable pgadmin4
usermod -a -G docker ec2-user
systemctl start docker
systemctl enable docker
# start pgAdmin4
docker run -d -p 62315:80 \
    -e "PGADMIN_DEFAULT_EMAIL=${pgAdmin4UserName}" \
    -e "PGADMIN_DEFAULT_PASSWORD=${pgAdmin4Password}" \
    -d dpage/pgadmin4

#start gradio app
cd /home/ec2-user
#deploy ChatBotWebUI
git clone https://github.com/nimysan/ChatBotWebUI.git
chown -R ec2-user:ec2-user ChatBotWebUI
cd ChatBotWebUI

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
chown ec2-user:ec2-user config.json
su ec2-user -s deploy.sh
echo "########### userdata ###########"