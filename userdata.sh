#!/bin/bash
dnf update -y
dnf install git postgresql15 pip docker -y
echo install ok
usermod -a -G docker ec2-user

systemctl start docker
systemctl enable docker

## 等待服务启动完成
#while ! systemctl is-active docker; do
#    sleep 1
#done
#echo "hhhhh"

# start pgAdmin4
docker run -d -p 62315:80 \
    -e "PGADMIN_DEFAULT_EMAIL=${pgAdmin4UserName}" \
    -e "PGADMIN_DEFAULT_PASSWORD=${pgAdmin4Password}" \
    -d dpage/pgadmin4

cd /home/ec2-user
#deploy ChatBotWebUI
git clone https://github.com/nimysan/ChatBotWebUI.git
chown -R ec2-user ChatBotWebUI
cd ChatBotWebUI

#初始化配置
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

pip3 install -r requirements.txt
nohup python3 webui.py &
echo "########### userdata ###########"

tail -f nohup.out