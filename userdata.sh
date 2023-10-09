#!/bin/bash
dnf update -y
dnf install git postgresql15 pip docker nginx -y

#enable pgadmin4
usermod -a -G docker ec2-user
systemctl start docker
systemctl enable docker
# start pgAdmin4
docker run -d -p 62315:80 \
    -e "PGADMIN_DEFAULT_EMAIL=${pgAdmin4UserName}" \
    -e "PGADMIN_DEFAULT_PASSWORD=${pgAdmin4Password}" \
    dpage/pgadmin4

curl https://raw.githubusercontent.com/nimysan/ChatBotWebUI/main/nginx.conf -o /etc/nginx/nginx.conf
systemctl enable nginx
systemctl start nginx
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
  "cognito": {
      "poolId": "${cognito_pool_id}",
      "clientId": "${cognito_client_id}"
  },
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
  },
  "prompts": {
    "condense_prompt": "\u9274\u4e8e\u4ee5\u4e0b\u5bf9\u8bdd\u548c\u540e\u7eed\u95ee\u9898\uff0c\u5c06\u540e\u7eed\u95ee\u9898\u6539\u5199\u4e3a\n\u662f\u4e00\u4e2a\u72ec\u7acb\u7684\u95ee\u9898\uff0c\u7528\u5176\u539f\u59cb\u8bed\u8a00\u3002\u786e\u4fdd\u907f\u514d\u4f7f\u7528\u4efb\u4f55\u4e0d\u6e05\u695a\u7684\u4ee3\u8bcd\u3002\nChat History:\n{chat_history}\n\u63a5\u4e0b\u6765\u7684\u63d0\u95ee: {question}\n\u8f6c\u5316\u540e\u7684\u95ee\u9898:",
    "qa_conversation_prompt": "\u4f7f\u7528\u4ee5\u4e0b\u4e0a\u4e0b\u6587\u6765\u56de\u7b54\u6700\u540e\u7684\u95ee\u9898\u3002\n                \u5982\u679c\u4f60\u4e0d\u77e5\u9053\u7b54\u6848\uff0c\u5c31\u8bf4\u4f60\u4e0d\u77e5\u9053\uff0c\u4e0d\u8981\u8bd5\u56fe\u7f16\u9020\u7b54\u6848\u3002\n                \u6700\u591a\u4f7f\u7528\u4e09\u4e2a\u53e5\u5b50\uff0c\u5e76\u5c3d\u53ef\u80fd\u4fdd\u6301\u7b54\u6848\u7b80\u6d01\u3002\n                \u603b\u662f\u8bf4\u201c\u8c22\u8c22\u60a8\u7684\u63d0\u95ee\uff01\u201d\u5728\u7b54\u6848\u7684\u5f00\u5934\u3002\u59cb\u7ec8\u5728\u7b54\u6848\u4e2d\u8fd4\u56de\u201c\u6765\u6e90\u201d\u90e8\u5206\u3002 \n                {context}\n                \u95ee\u9898: {question}\n                \u6211\u7684\u7b54\u6848\u662f:",
    "qa_prompt": "\u4e0b\u9762\u5c06\u7ed9\u4f60\u4e00\u4e2a\u201c\u95ee\u9898\u201d\u548c\u4e00\u4e9b\u201c\u5df2\u77e5\u4fe1\u606f\u201d\uff0c\u8bf7\u5224\u65ad\u8fd9\u4e2a\u201c\u95ee\u9898\u201d\u662f\u5426\u53ef\u4ee5\u4ece\u201c\u5df2\u77e5\u4fe1\u606f\u201d\u4e2d\u5f97\u5230\u7b54\u6848\u3002\n                \u82e5\u53ef\u4ee5\u4ece\u201c\u5df2\u77e5\u4fe1\u606f\u201d\u4e2d\u83b7\u53d6\u7b54\u6848\uff0c\u8bf7\u76f4\u63a5\u8f93\u51fa\u7b54\u6848\u3002\n                \u82e5\u4e0d\u53ef\u4ee5\u4ece\u201c\u5df2\u77e5\u4fe1\u606f\u201d\u4e2d\u83b7\u53d6\u7b54\u6848\uff0c\u8bf7\u56de\u7b54\u201c\u6839\u636e\u5df2\u77e5\u4fe1\u606f\u65e0\u6cd5\u56de\u7b54\u201d\u3002ALWAYS return a \"SOURCE\" part in your answer from.\n\n                 ==================================== \n                \u5df2\u77e5\u4fe1\u606f:\n                {summaries}\n                ====================================\n                \u95ee\u9898\uff1a\n                {question}\n                ====================================\n                 AI:\n                 Sources:"
  }
}
EOF
chown ec2-user:ec2-user config.json
su ec2-user -s deploy.sh > dlog 2>&1
echo "########### userdata ###########"