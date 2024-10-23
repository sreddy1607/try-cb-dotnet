/*
=======================================================================================
This file is being updated constantly by the DevOps team to introduce new enhancements
based on the template.  If you have suggestions for improvement,
please contact the DevOps team so that we can incorporate the changes into the
template.  In the meantime, if you have made changes here or don't want this file to be
updated, please indicate so at the beginning of this file.
=======================================================================================
*/

def branch = env.BRANCH_NAME ?: "master"
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
            - emptyDir: {}
              name: varlibcontainers
            - name: jenkins-trusted-ca-bundle
              configMap:
                name: jenkins-trusted-ca-bundle
                defaultMode: 420
                optional: true
          containers:
            - name: dotnet
              image: 136299550619.dkr.ecr.us-west-2.amazonaws.com/cammismspapp:1.0.34
              tty: true
              command: ["/bin/bash"]
              securityContext:
                privileged: true
              workingDir: ${workingDir}
              envFrom:
                - configMapRef:
                    name: jenkins-agent-env
                    optional: true
              env:
                - name: HOME
                  value: ${workingDir}
                - name: BRANCH
                  value: ${branch}
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
            - name: node
              image: registry.access.redhat.com/ubi8/nodejs-18:latest
              tty: true
              command: ["/bin/bash"]
              securityContext:
                privileged: true
              workingDir: ${workingDir}
              securityContext:
                privileged: true
              envFrom:
                - configMapRef:
                    name: jenkins-agent-env
                    optional: true
              env:
                - name: HOME
                  value: ${workingDir}
                - name: BRANCH
                  value: ${branch}
                - name: GIT_SSL_CAINFO
                  value: "/etc/pki/tls/certs/ca-bundle.crt"
              volumeMounts:
                - name: jenkins-trusted-ca-bundle
                  mountPath: /etc/pki/tls/certs
            - name: aws-boto3
              image: 136299550619.dkr.ecr.us-west-2.amazonaws.com/cammisboto3:1.0.1
              tty: true
              command: ["/bin/bash"]
              workingDir: ${workingDir}
              envFrom:
                - configMapRef:
                    name: jenkins-agent-env
                    optional: true
              env:
                - name: HOME
                  value: ${workingDir}
                - name: BRANCH
                  value: ${branch}
                - name: GIT_SSL_CAINFO
                  value: "/etc/pki/tls/certs/ca-bundle.crt"
              volumeMounts:
                - name: jenkins-trusted-ca-bundle
                  mountPath: /etc/pki/tls/certs
      """
    }
  }

  options {
    timestamps()
    disableConcurrentBuilds()
    timeout(time:5 , unit: 'HOURS')
    skipDefaultCheckout()
    buildDiscarder(logRotator(numToKeepStr: '20'))
  }

  environment {
    env_current_git_commit=""
    env_accesskey=""
    env_secretkey=""
    env_tag_name=""
    env_deploy_env=""
    env_DEPLOY_ENVIRONMENT="false"
    env_DEPLOY_FILES="true"
    env_DEPLOY_CONFIG="false"
    env_release_type=""
  }

  stages {
    stage("initialize") {
      steps {
        container(name: "node") {
          script {

            properties([
              parameters([
                choice(name: 'RELEASE_TYPE', choices: ['PATCH','MINOR','MAJOR'], description: 'Enter Release type'),
                booleanParam(name: 'USE_GIT_TAG', defaultValue: false, description: 'Use the selected git tag instead of LATEST commit'),
                gitParameter(name: 'GIT_TAG', defaultValue: 'rais-eis-app_from_dev', description: 'git tag', type: 'PT_TAG'),
                string(name: 'GIT_SHA',defaultValue: 'enter git sha(8+ chars)', description: 'enter git sha that you want to deploy') 
              ])
            ])

            deleteDir()

            env_release_type = params.RELEASE_TYPE
            echo 'checkout source  and get the commit id'
            env_current_git_commit = checkout(scm).GIT_COMMIT

            // get the short version of commit
            env_current_git_commit="${env_current_git_commit[0..7]}"

            env_deploy_env = "DEV"

            echo "Current deployment environment is ${env_deploy_env}"

            env_tag_name = "${branch}_${BUILD_NUMBER}_${env_current_git_commit}"

            if (params.USE_GIT_TAG == true) {
              env_current_git_commit = params.GIT_TAG
            }

            if ( ! params.GIT_SHA.contains("enter") ) {
              env_current_git_commit = params.GIT_SHA
            }

            echo "Tag to be applied is: ${env_tag_name}"
            echo "Tag will be applied to: ${env_current_git_commit}"


            if ( ( params.GIT_SHA.contains("enter")) && (params.USE_GIT_TAG == false)) {
              // use latest commit to build and deploy to DEV
              withCredentials([usernamePassword(credentialsId: "github-key", usernameVariable: 'NUSER', passwordVariable: 'NPASS')]) {
                sh """#!/bin/bash
                  git config --global --add safe.directory '*'
                  git config --system --add safe.directory '*' 
                  echo "The commit hash from the latest git current commit is ${env_current_git_commit}"
                  echo "The current build number is: ${BUILD_NUMBER}"
                  echo "The current branch is: ${branch}"
                  tagMessage="${env_tag_name}"
                  git checkout \${branch}
                  echo "Checked out ${branch}"
                  git config  --global user.email "jenkins@cammis.com"
                  git config  --global user.name "jenkins"
                  echo "The tagMessage is \$tagMessage"
                  echo "About to fetch"
                  git fetch --quiet --tags https://${NUSER}:${NPASS}@github.com/ca-mmis/rais-eis-app.git
                  echo "About to tag commit with build and branch"
                  git tag -f -a "\$tagMessage" -m "tag build" ${env_current_git_commit}
                  echo "About to push tag"
                  git push -f https://${NUSER}:${NPASS}@github.com/ca-mmis/rais-eis-app.git \$tagMessage
                  echo "Push is done"
                  echo "Checking out selected commit"
                  git checkout ${env_current_git_commit}
                  git diff-tree --no-commit-id --name-only ${env_current_git_commit} -r > commit-changes.txt
                  echo "Checkout out ${env_current_git_commit}"
                  """
              }
            } else if ( (params.USE_GIT_TAG == true) || ( ! params.GIT_SHA.contains("enter")) ) {
  			      // using a git tag or git sha, ${env_current_git_commit}" is now the commit tag selected or git sha entered
              withCredentials([usernamePassword(credentialsId: "github-key", usernameVariable: 'NUSER', passwordVariable: 'NPASS')]) {
                sh """#!/bin/bash
                  echo "Checking out SHA or TAG: ${env_current_git_commit}"
                  git config --global --add safe.directory '*'
                  git config --system --add safe.directory '*' 
                  git checkout \${env_current_git_commit}
                  echo "Since this is a previous commit, need to rebuild everything"
                  echo "DrugRebate" > commit-changes.txt
                  echo "NetFileServDownload" >> commit-changes.txt
                  echo "EisAuth" >> commit-changes.txt
                  cat commit-changes.txt
                  echo "Checked out ${env_current_git_commit}"
                  """
              }
            } 
          } //END script
        } //END container node
      } //END steps
    } //END stage

    stage('Build') {
      steps {
        container(name: "node") {
          script {
           sh '''
              echo "Creating directory to build into and deploy from."
              echo "Need to add the placeholder.txt file so AWS CodeDeploy deploys an empty directory"
              mkdir devops/codedeploy/eiswapi
              touch devops/codedeploy/eiswapi/placeholder.txt
              mkdir devops/codedeploy/eisauthapi
              touch devops/codedeploy/eisauthapi/placeholder.txt
              mkdir devops/codedeploy/eisfilesvc
              touch devops/codedeploy/eisfilesvc/placeholder.txt
            '''
          }
        }

        container(name: "dotnet") {
          script {
           sh '''
             echo "Checking if NetFileServDownload needs to be built/deployed..."
             if grep -q NetFileServDownload commit-changes.txt; then
               echo "Running NetFileServDownload build"
               cd  NetFileServDownload
               dotnet publish -o ../devops/codedeploy/eisfilesvc --verbosity detailed --property:ImportWindowsDesktopTargets=false
               cd ..
             else
               echo "No changes for NetFileServDownload, build not needed"
             fi

             echo "Checking if EisAuth needs to be built/deployed..."
             if grep -q EisAuth commit-changes.txt; then
               echo "Running EisAuth build"
               cd  EisAuth
               dotnet publish -o ../devops/codedeploy/eisauthapi --verbosity detailed --property:ImportWindowsDesktopTargets=false
               cd ..
             else
               echo "No changes for EisAuth, build not needed"
             fi

             echo "Checking if DrugRebate needs to be built/deployed..."
             if grep -q -e DrugRebate -e DownFromEgistics -e Sorting commit-changes.txt; then
               echo "Running DrugRebate build"
               cd  DrugRebate
               dotnet publish -o ../devops/codedeploy/eiswapi --verbosity detailed --property:ImportWindowsDesktopTargets=false
               cd ..

               echo "Running DownFromEgistics build"
               cd  DownFromEgisticsCSharp
               dotnet publish -o ../devops/codedeploy/DownFromEgisticDLL --verbosity detailed --property:ImportWindowsDesktopTargets=false
               echo "Copying DLL to the directory for DrugRebate"
               cp ../devops/codedeploy/DownFromEgisticDLL/DownFromEgistics.dll ../devops/codedeploy/eiswapi
               cd ..

               echo "Running Sorting build with"
               cd Sorting
               dotnet publish --verbosity detailed --property:ImportWindowsDesktopTargets=false
               echo "Copying DLL to the directory for DrugRebate"
               cp Sorting/bin/Release/net8.0/Sorting.dll ../devops/codedeploy/eiswapi
               cd ..
             else
               echo "No changes for DrugRebate, build not needed"
             fi
            '''
          }
        }
      } // end of steps
    } // end of Build stage

    stage('Sonar Scan') {
      when {
        expression {
          env_DEPLOY_FILES == "true"
        }
      }
      steps {
        container(name: "dotnet") {
          script {
            sh """#!/bin/bash
			          set +e
                echo 'Sonar Scan Stage....\n\n'
                if grep -q -e DrugRebate -e DownFromEgistics -e Sorting commit-changes.txt; then
			        	  cd  DrugRebate
	        			  dotnet tool install dotnet-sonarscanner --global
		        		  export PATH="$PATH:/home/jenkins/agent/.dotnet/tools"
		        		  dotnet sonarscanner begin /key:"rais-eis-app" /d:sonar.host.url="http://sonarqube-tools.apps.bld.cammis.medi-cal.ca.gov"
                  dotnet publish 
                  dotnet sonarscanner end
                else
                  echo "Did not build DrugRebate -- no sonar scan"
                fi
              """
          }
        }
      }
    }

    stage('Prepare Deployment') {
      steps {
        container(name: "aws-boto3") {
          script {
            withCredentials([string(credentialsId: 'APPROLE_SECRET_ID', variable: 'APPROLE_SECRET_ID')]) {
              sh """#!/bin/bash
                echo "Preparing Deployment"
                sed -i "s,{DEPLOY_FILES},${env_DEPLOY_FILES}," devops/codedeploy/after-install.bat
                sed -i "s,{server-environment},${env_deploy_env}," devops/codedeploy/serverconfig/index.html
              """
            }
          } // end of script
        } // end of container
      } // end of steps
    }  // end of Prepare Deployment Stage

    stage('Deploy') {
      steps {
        container(name: "aws-boto3") {
          script {
            sh """#!/bin/bash
            echo "Deploy Using AWS CodeDeploy"
            """
            withCredentials([aws(accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'jenkins-ecr', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
              step([$class: 'AWSCodeDeployPublisher',
                  applicationName: "rais-eis-${env_deploy_env}",
                  awsAccessKey: "${AWS_ACCESS_KEY_ID}",
                  awsSecretKey: "${AWS_SECRET_ACCESS_KEY}",
                  credentials: 'awsAccessKey',
                  deploymentConfig: "rais-eis-${env_deploy_env}-config",
                  deploymentGroupAppspec: false,
                  deploymentGroupName: "rais-eis-${env_deploy_env}-INPLACE-deployment-group",
                  deploymentMethod: 'deploy',
                  excludes: '', iamRoleArn: '', includes: '**', pollingFreqSec: 15, pollingTimeoutSec: 900, proxyHost: '', proxyPort: 0,
                  region: 'us-west-2', s3bucket: 'dhcs-codedeploy-app', 
                  subdirectory: 'devops/codedeploy', versionFileName: '', waitForCompletion: true])
            }
          } // end of script
        } // end of container

        container(name: "jnlp") {
          lock(resource: 'deployments-github-repo',inversePrecedence: false ) {
           dir("${WORKSPACE}/deployrepo"){
              withCredentials([usernamePassword(credentialsId: "github-key", usernameVariable: 'NUSER', passwordVariable: 'NPASS')]) {
                sh """
                  git clone https://${NUSER}:${NPASS}@github.com/ca-mmis/deployments-combined-devops.git --depth=1
                  git config  --global user.email "jenkins@cammis.com"
                  git config  --global user.name "jenkins"

                  cd deployments-combined-devops
                  git checkout master
                  git pull

                  cp ${WORKSPACE}/devops/codedeploy/serverconfig/index.html ${WORKSPACE}/deployrepo/deployments-combined-devops/rais-eis-app/dev

                  updates_to_deploy=false

                  if grep -q EisAuth ${WORKSPACE}/commit-changes.txt; then
                    echo "EisAuth was built, need to update deployment repository"
                    echo "Comparing the directories with DIFF:"
                    set +e
                    diff -r ${WORKSPACE}/devops/codedeploy/eisauthapi ${WORKSPACE}/deployrepo/deployments-combined-devops/rais-eis-app/dev/eisauthapi
                    set -e
                    # remove and replace deployment for eisauthapi
                    rm -r ${WORKSPACE}/deployrepo/deployments-combined-devops/rais-eis-app/dev/eisauthapi/*
                    cp -a ${WORKSPACE}/devops/codedeploy/eisauthapi/. ${WORKSPACE}/deployrepo/deployments-combined-devops/rais-eis-app/dev/eisauthapi/
                    rm ${WORKSPACE}/deployrepo/deployments-combined-devops/rais-eis-app/dev/eisauthapi/placeholder.txt
                    updates_to_deploy=true
                  else
                    echo "No updated deployment for EisAuth"
                  fi

                  if grep -q -e DrugRebate -e DownFromEgistics -e Sorting ${WORKSPACE}/commit-changes.txt; then
                    echo "DrugRebate was built, need to update deployment repository"
                    # remove and replace deployment for eiswapi
                    echo "Comparing the directories with DIFF:"
                    set +e
                    diff -r ${WORKSPACE}/devops/codedeploy/eiswapi ${WORKSPACE}/deployrepo/deployments-combined-devops/rais-eis-app/dev/eiswapi
                    set -e
                    rm -r ${WORKSPACE}/deployrepo/deployments-combined-devops/rais-eis-app/dev/eiswapi/*
                    cp -a ${WORKSPACE}/devops/codedeploy/eiswapi/. ${WORKSPACE}/deployrepo/deployments-combined-devops/rais-eis-app/dev/eiswapi/
                    rm ${WORKSPACE}/deployrepo/deployments-combined-devops/rais-eis-app/dev/eiswapi/placeholder.txt
                    updates_to_deploy=true
                  else
                    echo "No updated deployment for DrugRebate"
                  fi

                  if grep -q NetFileServDownload ${WORKSPACE}/commit-changes.txt; then
                    echo "EisFileSvc was built, need to update deployment repository"
                    echo "Comparing the directories with DIFF:"
                    set +e
                    diff -r ${WORKSPACE}/devops/codedeploy/eisfilesvc ${WORKSPACE}/deployrepo/deployments-combined-devops/rais-eis-app/dev/eisfilesvc
                    set -e
                    # remove and replace deployment for eisfilesvc
                    rm -r ${WORKSPACE}/deployrepo/deployments-combined-devops/rais-eis-app/dev/eisfilesvc/*
                    cp -a ${WORKSPACE}/devops/codedeploy/eisfilesvc/. ${WORKSPACE}/deployrepo/deployments-combined-devops/rais-eis-app/dev/eisfilesvc/
                    rm ${WORKSPACE}/deployrepo/deployments-combined-devops/rais-eis-app/dev/eisfilesvc/placeholder.txt
                    updates_to_deploy=true
                  else
                    echo "No updated deployment for EisAuth"
                  fi

                  if [ "\$updates_to_deploy" = true ] ; then
                    touch rais-eis-app/updates_to_deploy
                  fi


                """
                
                script {
                   incrementVersion()
                }

                sh """
                  cd ${WORKSPACE}/deployrepo/deployments-combined-devops
                  if [ -f rais-eis-app/updates_to_deploy ] ; then
                    echo 'Pushing to the deployment repository'
                    rm rais-eis-app/updates_to_deploy
                    echo "Will tag deploy repo with: \"Updated build artifacts for rais-eis-app build ${env_tag_name}\""
                    git add -Av
                    git commit -m "Updated build artifacts for rais-eis-app build ${env_tag_name}"
                    git push https://${NUSER}:${NPASS}@github.com/ca-mmis/deployments-combined-devops.git
                  else
                    echo "Nothing needs to be pushed to deployment repository"
                  fi
                  pwd
                """
              } //end withCredentials
            } //end dir
          } //end lock
        }  //end container

      } // end of steps
    } // end of Deploy stage
  } // end of stages

  //pipeline post actions
  post {
    always {
        echo "Build Process complete."
    } // always

    success {
        echo "Build Process was success."
    } //success

    unstable {
        echo "Build is unstable."
    } // unstable

    aborted {
        echo "Pipeline aborted."
    } // aborted

    failure {
        echo "Build encountered failures ."
    } // failure

    changed {
        echo "Build content was changed."
    } // changed

  } // post
}


def incrementVersion() {
  def versionInfo = readYaml file: "deployments-combined-devops/rais-eis-app/dev/release/version.yaml"
  def Integer newMinor = versionInfo.app.version.minor
  def Integer newMajor = versionInfo.app.version.major
  def Integer newPatch = versionInfo.app.version.patch
  
  if(env_release_type == "PATCH" ) {
    newPatch = newPatch+1
  } else if (env_release_type == "MINOR") {
    newMinor = newMinor+1
    newPatch = 0
  } else if (env_release_type == "MAJOR") {
    newMajor = newMajor+1
    newMinor = 0
    newPatch = 0
  }
  
  def updatedVersionInfo = versionInfo
  def buildDate = new Date()
  updatedVersionInfo.app.version.patch=newPatch
  updatedVersionInfo.app.version.major=newMajor
  updatedVersionInfo.app.version.full=updatedVersionInfo.app.version.major+"."+updatedVersionInfo.app.version.minor+"."+updatedVersionInfo.app.version.patch
  updatedVersionInfo.app.build.date=buildDate.format("yyyy-MM-dd_HH:mm")
  updatedVersionInfo.app.build.number="${BUILD_NUMBER}"
  updatedVersionInfo.app.build.commit="${env_current_git_commit}"
  println "Saving version.yaml file...\n${updatedVersionInfo}"
  writeYaml file: "deployments-combined-devops/rais-eis-app/dev/release/version.yaml", data: updatedVersionInfo, overwrite: true
}
