# Welcome to your CDK Java project!

This is a blank project for CDK development with Java.

The `cdk.json` file tells the CDK Toolkit how to execute your app.

It is a [Maven](https://maven.apache.org/) based project, so you can open this project with any Maven compatible Java IDE to build and run tests.

## Useful commands

 * `mvn package`     compile and run tests
 * `cdk ls`          list all stacks in the app
 * `cdk synth`       emits the synthesized CloudFormation template
 * `cdk deploy`      deploy this stack to your default AWS account/region
 * `cdk diff`        compare deployed stack with current state
 * `cdk docs`        open CDK documentation

Enjoy!


## 报错了 请升级Node版本/cdk版本. 或找个新的环境测试. 大概率跟代码无关

```bash
#deploy 
cdk deploy --debug -vvv --parameters ec2KeyPair=us-east-1
```


### 如何创建一个serverless v2版本的数据库

https://repost.aws/questions/QUTCKQz8CPSnSExbt-Oi-8xQ/unavailable-serverless-db-engine-mode-error

```bash
# install psql client
sudo dnf install postgresql15 -y

```

### RDS管理

安装pgAdmin4 --- https://github.com/twtrubiks/docker-pgadmin4-tutorial


```bash
aws rds-data execute-statement --resource-arn <DB_CLUSTER_ARN> --secret-arn <SECRET_ARN> --database information_schema --sql "select * from information_schema.tables LIMIT 1" --region us-east-1
```

### 关于userdata运行的一些说明和注意事项

[user data](https://docs.aws.amazon.com/zh_cn/AWSEC2/latest/UserGuide/user-data.html#user-data-shell-scripts)