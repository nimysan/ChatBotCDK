#!/bin/bash

sudo dnf install git postgresql15 pip -y && git clone https://github.com/nimysan/ChatBotWebUI.git && cd  ChatBotWebUI &&chmod a+x ./deploy.sh &&  ./deploy.sh