package com.myorg;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.cloudwatch.actions.Ec2InstanceAction;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcLookupOptions;
import software.constructs.Construct;
import software.amazon.awscdk.services.ec2.*;
// import software.amazon.awscdk.Duration;
// import software.amazon.awscdk.services.sqs.Queue;

public class ChatBotCdkStack extends Stack {
    public ChatBotCdkStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public ChatBotCdkStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // The code that defines your stack goes here

        // example resource
        // final Queue queue = Queue.Builder.create(this, "ChatBotCdkQueue")
        //         .visibilityTimeout(Duration.seconds(300))
        //         .build();

        // 创建一个输入参数对象
        CfnParameter vpcIdParameter = CfnParameter.Builder.create(this, "VpcId")
                .type("String")
                .description("VPC ID")
                .build();
        // key pair name
        CfnParameter ec2KeyPairName = CfnParameter.Builder.create(this, "ec2KeyPair")
                .type("String")
                .description("EC2 Key Pair name")
                .build();


        // key pair name
        CfnParameter instanceTypeParameter = CfnParameter.Builder.create(this, "instanceType")
                .type("String")
                .description("instanceTypeParameter")
                .build();
        IVpc vpc = Vpc.fromLookup(this, "VPC", VpcLookupOptions.builder()
                // This imports the default VPC but you can also
                // specify a 'vpcName' or 'tags'.
                .vpcId(vpcIdParameter.getValueAsString())
                .isDefault(true)
                .build());

        SecurityGroup mySecurityGroup = SecurityGroup.Builder.create(this, "SecurityGroup")
                .vpc(vpc)
                .description("Allow ssh access to ec2 instances")
                .allowAllOutbound(true)
                .build();
        mySecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(22), "allow ssh access from the world");
        //TODO 连接数据库所在

        //创建EC2
        Instance.Builder.create(this, "Instance2")
                .vpc(vpc)
                .instanceType(InstanceType.of(InstanceClass.C5, InstanceSize.LARGE))
                .machineImage(MachineImage.latestAmazonLinux2023())
                .securityGroup(mySecurityGroup)
                .keyName(ec2KeyPairName.getValueAsString())
                .build();

        //
//
//
//
//        SecurityGroup securityGroupForEC2 = createSecurityGroup(this, vpc);
//        Ec2Instance createEC2(this, vpc, securityGroupForEC2, ec2KeyPairName);

//        // 使用输入参数创建一个VPC对象
//        IVpc vpc = Vpc.fromLookup(this, "MyVpc", VpcLookupOptions.builder()
//                .vpcId(vpcIdParameter.getValueAsString())
//                .build());
//
//        // 在这里可以使用vpc对象进行其他资源的创建，例如创建EC2实例等
//
//        // 输出VPC ID
//        CfnOutput.Builder.create(this, "OutputVpcId")
//                .value(vpc.getVpcId())
//                .description("VPC ID")
//                .build();
    }

    private void createSecurityGroup(ChatBotCdkStack chatBotCdkStack, IVpc vpc) {
    }
}
