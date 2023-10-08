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
  "openai_key": "${openai_key}",
  "bedrock_llm": {
    "region": "us-west-2",
    "model_id": "anthropic.claude-v2"
  },
  "bedrock_embeddings": {
    "region": "us-west-2",
    "model_id": "amazon.titan-embed-text-v1"
  },
  "bot": {
    "llm": "bedrock",
    "embeddings": "bedrock"
  },
  "sagemaker_embeddings_endpoint": {
    "endpoint": "huggingface-pytorch-inference-2023-09-28-08-24-28-262",
    "region": "us-west-2"
  },
  "sagemaker_llm_endpoint": {
    "endpoint": "ChatGLM-6B-SageMaker-2023-09-27-14-35-40-172",
    "region": "us-west-2"
  }
}
EOF
chown ec2-user:ec2-user config.json
su ec2-user -s deploy.sh > dlog 2>&1
echo "########### userdata ###########"