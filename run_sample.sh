cdk deploy --debug -vvv \
--parameters ec2KeyPair=us-east-1 \
--parameters databasePassword=Lxd%*1234 \
--parameters pgAdmin4UserName=sample@sample.com \
--parameters pgAdmin4Password=SampleAdmin \
--parameters pgDatabaseName=knowledge \
--parameters openaiKeyParam=sk-testxxxxx
#--vpcParam xxxx