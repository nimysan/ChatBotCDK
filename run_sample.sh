cdk deploy --debug -vvv \
--parameters ec2keypair=chatbot \
--parameters pgAdmin4UserName=sample@sample.com \
--parameters pgAdmin4Password=SampleAdmin \
--parameters databasename=knowledge \
--parameters databasepassword=Lxd%*1234 \
--parameters openaikey=sk-testxxxxx
#--vpcParam xxxxcdk boot