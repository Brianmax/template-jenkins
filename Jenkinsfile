pipeline {
    agent any

    tools {
        // Must match the name configured in Manage Jenkins → Tools → JDK installations
        jdk 'JDK-17'
    }

    triggers {
        githubPush()
    }

    environment {
        APP_NAME    = 'todos-api'
        JAR_VERSION = '0.0.1-SNAPSHOT'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log -1 --format="%h %s"'
            }
        }

        stage('Build') {
            steps {
                sh 'chmod +x mvnw'
                sh './mvnw clean compile -B'
            }
        }

        stage('Test') {
            steps {
                sh './mvnw test -B'
            }
            post {
                always {
                    // Publish JUnit test results — visible under the build's "Test Result" link
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Package') {
            steps {
                // Tests already ran; skip them here to avoid running twice
                sh './mvnw package -DskipTests -B'
            }
        }

        stage('Archive Artifacts') {
            steps {
                archiveArtifacts(
                    artifacts: "target/${APP_NAME}-${JAR_VERSION}.jar",
                    fingerprint: true
                )
            }
        }
    }

    post {
        success {
            echo "Pipeline succeeded. Artifact: target/${APP_NAME}-${JAR_VERSION}.jar"
            withCredentials([string(credentialsId: 'github-token', variable: 'GITHUB_TOKEN')]) {
                sh """
                    curl -s -X POST \
                      -H "Authorization: token \$GITHUB_TOKEN" \
                      -H "Content-Type: application/json" \
                      -d '{"state":"success","context":"Jenkins CI","description":"Build passed"}' \
                      "https://api.github.com/repos/\$(echo \$GIT_URL | sed 's|.*github.com/||;s|.git\$||')/statuses/\${GIT_COMMIT}"
                """
            }
        }
        failure {
            echo "Pipeline FAILED — review the stage logs above for details."
            withCredentials([string(credentialsId: 'github-token', variable: 'GITHUB_TOKEN')]) {
                sh """
                    curl -s -X POST \
                      -H "Authorization: token \$GITHUB_TOKEN" \
                      -H "Content-Type: application/json" \
                      -d '{"state":"failure","context":"Jenkins CI","description":"Build failed"}' \
                      "https://api.github.com/repos/\$(echo \$GIT_URL | sed 's|.*github.com/||;s|.git\$||')/statuses/\${GIT_COMMIT}"
                """
            }
        }
    }
}

/*
 * ADAPTATION NOTES
 * ─────────────────
 * Gradle project: replace './mvnw <goal>' with './gradlew <task>'
 *   - compile  → classes
 *   - test     → test  (junit glob: 'build/test-results/**\/*.xml')
 *   - package  → bootJar
 *
 * Docker agent (no tool config needed in Jenkins):
 *   agent {
 *     docker {
 *       image 'eclipse-temurin:17-jdk-alpine'
 *       args  '-v $HOME/.m2:/root/.m2'
 *     }
 *   }
 *
 * Docker build stage (add after Archive Artifacts, requires Docker socket mounted):
 *   stage('Docker Build') {
 *     steps {
 *       sh "docker build -t ${APP_NAME}:${BUILD_NUMBER} ."
 *       sh "docker tag ${APP_NAME}:${BUILD_NUMBER} ${APP_NAME}:latest"
 *     }
 *   }
 */
