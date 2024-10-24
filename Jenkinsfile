def branch = env.BRANCH_NAME ?: "ecr"
def namespace = env.NAMESPACE ?: "dev"
def workingDir = "/home/jenkins/agent"

pipeline {
  agent {
    kubernetes {
      yaml """
        apiVersion: v1
        kind: Pod
        spec:
          serviceAccountName: jenkins
          volumes:
            - name: dockersock
              hostPath:
                path: /var/run/docker.sock
            - name: varlibcontainers
              emptyDir: {}
            - name: jenkins-trusted-ca-bundle
              configMap:
                name: jenkins-trusted-ca-bundle
                defaultMode: 420
                optional: true
          containers:
            - name: jnlp
              securityContext:
                privileged: true
              envFrom:
                - configMapRef:
                    name: jenkins-agent-env
                    optional: true
              env:
                - name: GIT_SSL_CAINFO
                  value: "/etc/pki/tls/certs/ca-bundle.crt"
              volumeMounts:
                - name: jenkins-trusted-ca-bundle
                  mountPath: /etc/pki/tls/certs
            - name: cammismsbuild
              image: 136299550619.dkr.ecr.us-west-2.amazonaws.com/cammismspapp:1.0.42
              tty: true
              command: ["/bin/bash"]
              securityContext:
                privileged: true
              workingDir: "${workingDir}"
              envFrom:
                - configMapRef:
                    name: jenkins-agent-env
                    optional: true
              env:
                - name: HOME
                  value: "${workingDir}"
                - name: BRANCH
                  value: "${branch}"
                - name: NEXUS_ACCESS_TOKEN
                  valueFrom:
                    secretKeyRef:
                      name: jenkins-token-qqsb2
                      key: token
                - name: GIT_SSL_CAINFO
                  value: "/etc/pki/tls/certs/ca-bundle.crt"
              volumeMounts:
                - name: jenkins-trusted-ca-bundle
                  mountPath: /etc/pki/tls/certs
      """
    }
  }

  options {
    disableConcurrentBuilds()
    timeout(time: 5, unit: 'HOURS')
    skipDefaultCheckout()
    buildDiscarder(logRotator(numToKeepStr: '10'))
  }

  environment {
    env_git_branch_type = "feature"
    env_git_branch_name = ""
    env_current_git_commit = ""
    env_skip_build = "false"
    env_stage_name = ""
    env_step_name = ""
    
    NEXUS_URL = "https://nexusrepo-tools.apps.bld.cammis.medi-cal.ca.gov"
    NEXUS_REPOSITORY = "cammis-msbuild-repo"
    NEXUS_CREDENTIALS = credentials('nexus-credentials')
    CERTIFICATE_PATH = "/etc/pki/tls/certs/ca-bundle.crt"
    DOTNET_NUGET_SIGNATURE_VERIFICATION = "false"
  }

  stages {
    stage("Initialize") {
      steps {
        container(name: "cammismsbuild") {
          script {
            properties([
              parameters([])
            ])

            env_stage_name = "initialize"
            env_step_name = "checkout"

            deleteDir()
            echo 'Checkout source and get the commit ID'
            env_current_git_commit = checkout(scm).GIT_COMMIT

            echo 'Loading properties file'
            env_step_name = "load properties"
            // load the pipeline properties
            // load(".jenkins/pipelines/Jenkinsfile.ecr.properties")

            env_step_name = "set global variables"
            echo 'Initialize Slack channels and tokens'
          }
        }
      }
    }

    stage('Restore Dependencies') {
      steps {
        container('cammismsbuild') {
          sh 'dotnet restore --no-cache'
        }
      }
    }

    stage('Build') {
      steps {
        container('cammismsbuild') {
          sh 'dotnet build --configuration Release'
        }
      }
    }

    stage('Publish') {
      steps {
        container('cammismsbuild') {
          sh 'dotnet publish --configuration Release --output ./publish'
        }
      }
    }

    stage('Pack NuGet Package') {
      steps {
        container('cammismsbuild') {
          sh 'dotnet pack -o ./publish'
        }
      }
    }

    stage('Push to Nexus') {
      steps {
        container('cammismsbuild') {
          script {
         
            sh '''
for file in publish/*.nupkg; do
  curl -k -v -u ${NEXUS_CREDENTIALS_USR}:${NEXUS_CREDENTIALS_PSW} -F "nuget.asset=@$file" "https://nexusrepo-tools.apps.bld.cammis.medi-cal.ca.gov/service/rest/v1/components?repository=${NEXUS_REPOSITORY}"

done
           '''
          
          }
        }
      }
    }
  }
}
