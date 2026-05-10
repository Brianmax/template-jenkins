# Jenkins CI/CD Guide — Multibranch Pipeline for Java (Todos API)

This guide walks you through running Jenkins as a Docker container, configuring it, and building a complete CI/CD pipeline for the **Todos API** Spring Boot application using a **Multibranch Pipeline** — the correct approach for real-world projects with feature branches and Pull Requests.

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Running Jenkins in Docker](#2-running-jenkins-in-docker)
3. [Configuring Jenkins](#3-configuring-jenkins)
4. [Project Overview](#4-project-overview)
5. [The Jenkinsfile](#5-the-jenkinsfile)
6. [Creating the Multibranch Pipeline](#6-creating-the-multibranch-pipeline)
7. [Webhook Triggers](#7-webhook-triggers)
8. [GitHub Status Reporting](#8-github-status-reporting)
9. [Branch Protection](#9-branch-protection)
10. [Jenkins Value in Practice](#10-jenkins-value-in-practice)
11. [Enhancement: Docker Image Build](#11-enhancement-docker-image-build)
12. [Troubleshooting](#12-troubleshooting)

---

## 1. Prerequisites

| Tool | Version | Required? | Purpose |
|------|---------|-----------|---------|
| Docker | 24+ | **Yes** | Runs Jenkins |
| Git | Any | **Yes** | Source control |
| ngrok | Any | **Yes** | Exposes local Jenkins to GitHub |
| Java JDK 17 | 17+ | Optional | Local builds without Docker |
| Maven | 3.9+ | Optional | Local builds (project includes Maven wrapper) |

### Install Docker

**macOS / Windows**: Download and install [Docker Desktop](https://www.docker.com/products/docker-desktop/).

**Linux (Ubuntu/Debian)**:
```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
newgrp docker
```

**Verify the installation:**
```bash
docker --version
docker compose version
```

### Install ngrok

ngrok exposes your local Jenkins to the internet so GitHub can send webhook payloads to it.

**macOS:**
```bash
brew install ngrok
```

**Other platforms**: Download from [ngrok.com/download](https://ngrok.com/download).

---

## 2. Running Jenkins in Docker

Jenkins is configured in `jenkins/docker-compose.yml`. It runs on port **8090** so it does not conflict with the Todos API which uses port 8080.

### 2.1 Start the Jenkins Container

Run this from the root of the repository:

```bash
docker compose -f jenkins/docker-compose.yml up -d
```

Expected output:
```
[+] Running 2/2
 ✔ Network jenkins_default  Created
 ✔ Container jenkins         Started
```

Verify it is running:
```bash
docker ps | grep jenkins
```

You should see `jenkins` listed with `Up` status and port `0.0.0.0:8090->8080/tcp`.

### 2.2 Retrieve the Initial Admin Password

```bash
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

Copy the 32-character hex string that is printed.

> If the command returns "No such file or directory", Jenkins has not finished starting yet. Run `docker compose -f jenkins/docker-compose.yml logs -f jenkins` and wait until you see `Jenkins is fully up and running`, then try again.

### 2.3 Unlock Jenkins in the Browser

Open **http://localhost:8090** in your browser.

1. Paste the password you copied → click **Continue**
2. On the "Customize Jenkins" screen, click **Install suggested plugins**
   - Jenkins downloads and installs ~20 plugins. This takes 2–4 minutes.
3. On "Create First Admin User", fill in your details → click **Save and Continue**
4. On "Instance Configuration", leave the URL as `http://localhost:8090/` → click **Save and Finish**
5. Click **Start using Jenkins**

### 2.4 Stopping and Restarting Jenkins

```bash
# Stop (data is preserved in the jenkins_home volume)
docker compose -f jenkins/docker-compose.yml down

# Start again — no setup wizard, your config is intact
docker compose -f jenkins/docker-compose.yml up -d
```

---

## 3. Configuring Jenkins

### 3.1 Verify Installed Plugins

Go to **Manage Jenkins → Plugins → Installed plugins** and confirm these are present:

| Plugin | Purpose |
|--------|---------|
| Git Plugin | Clones repositories |
| Pipeline | Enables declarative `Jenkinsfile` syntax |
| Pipeline: Stage View | Visual stage graph on the job page |
| JUnit | Publishes test reports with pass/fail details |
| GitHub plugin | Enables webhook triggers and GitHub server integration |

If any are missing: go to **Available plugins**, search by name, check the box, and click **Install**.

### 3.2 Configure the JDK Global Tool

Go to **Manage Jenkins → Tools**.

Scroll to **JDK installations** → click **Add JDK**:

| Field | Value |
|-------|-------|
| Name | `JDK-17` |
| Install automatically | ✓ checked |
| Version | Select the latest `jdk-17.x.x` from the dropdown |

Click **Save**.

> **Important**: The name `JDK-17` must match exactly what is in the `tools { jdk 'JDK-17' }` block of the `Jenkinsfile`.

### 3.3 Configure the GitHub Server

This allows Jenkins to post build statuses back to GitHub so Pull Requests can show green/red checks.

**Step 1 — Create a GitHub Personal Access Token (classic)**

In GitHub: **Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token (classic)**

| Field | Value |
|-------|-------|
| Note | `jenkins-status` |
| Expiration | 90 days (or your preference) |
| Scopes | ✓ `repo:status` |

Click **Generate token** and copy it — you will not see it again.

> Use **Tokens (classic)**, not Fine-grained tokens. The Jenkins GitHub plugin works reliably with classic tokens.

**Step 2 — Add the token to Jenkins credentials**

Go to **Manage Jenkins → Credentials → System → Global credentials → Add Credentials**:

| Field | Value |
|-------|-------|
| Kind | Secret text |
| Secret | your GitHub token |
| ID | `github-token` |
| Description | GitHub status token |

**Step 3 — Configure the GitHub Server**

Go to **Manage Jenkins → System** → scroll to the **GitHub** section → click **Add GitHub Server**:

| Field | Value |
|-------|-------|
| Name | `GitHub` |
| API URL | `https://api.github.com` |
| Credentials | select `github-token` |

Click **Test connection** — you should see `Credentials verified for user <your-username>`.

Click **Save**.

---

## 4. Project Overview

This repository is a **Spring Boot 3.3.5 REST API** for managing a to-do list. It is the application the Jenkins pipeline will build and test.

### Project Structure

```
todos-api/
├── Jenkinsfile                         # CI/CD pipeline definition
├── jenkins/
│   └── docker-compose.yml              # Runs the Jenkins container
├── pom.xml                             # Maven build (Java 17, Spring Boot 3.3.5)
└── src/
    ├── main/java/com/example/todos/
    │   ├── controller/TodoController.java
    │   ├── service/TodoService.java
    │   ├── repository/TodoRepository.java
    │   └── entity/Todo.java
    └── test/java/com/example/todos/
        └── service/TodoServiceTest.java    # 14 unit tests (Mockito, no DB)
```

### REST Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/todos` | List all todos |
| `GET` | `/api/todos/{id}` | Get a single todo |
| `POST` | `/api/todos` | Create a todo |
| `PUT` | `/api/todos/{id}` | Update a todo |
| `DELETE` | `/api/todos/{id}` | Delete a todo |

### Running Tests Locally

The unit tests use Mockito — no database required:

```bash
./mvnw test
```

Expected output:
```
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 5. The Jenkinsfile

The `Jenkinsfile` at the root of the repository defines the entire CI/CD pipeline. With a Multibranch Pipeline, Jenkins reads it automatically from every branch.

```groovy
pipeline {
    agent any

    tools {
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
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Package') {
            steps {
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
```

### Key points

| Block | Purpose |
|-------|---------|
| `triggers { githubPush() }` | Tells Jenkins to build when GitHub sends a push webhook |
| `checkout scm` | Clones the correct branch automatically — this is what makes Multibranch Pipeline work |
| `post { success / failure }` | Reports build status back to GitHub via the Status API so PRs show green/red |
| `GIT_COMMIT` | Populated automatically by `checkout scm` — identifies which commit to post the status to |

> **Why curl instead of `githubNotify`?** The `githubNotify` step requires an additional Jenkins plugin that is not installed by default. The curl approach works with any Jenkins installation and only needs the `github-token` credential we already configured.

---

## 6. Creating the Multibranch Pipeline

A **Multibranch Pipeline** automatically discovers all branches in your repository that contain a `Jenkinsfile`, creates a sub-pipeline for each one, and triggers builds independently per branch. This is the correct Jenkins job type for projects that use Pull Requests.

> **Why not a standard Pipeline job?** A standard Pipeline job builds one fixed branch. It cannot dynamically build feature branches pushed for PRs — you would have to change the branch specifier manually every time.

### Step 1 — Create the Job

1. Jenkins dashboard → **+ New Item**
2. Enter a name (e.g. `jenkins-example`)
3. Select **Multibranch Pipeline**
4. Click **OK**

### Step 2 — Configure Branch Sources

Under **Branch Sources** → click **Add source** → select **Git**:

| Field | Value |
|-------|-------|
| Project Repository | `https://github.com/Brianmax/jenkins-example` |
| Credentials | `- none -` (public repo) |

Leave all other settings at their defaults.

### Step 3 — Save

Click **Save**. Jenkins immediately scans the repository, discovers all branches with a `Jenkinsfile`, and triggers a build for each one.

You should see sub-pipelines appear for `main` and any other existing branches.

---

## 7. Webhook Triggers

Without a webhook, Jenkins only builds when you click **Build Now**. A webhook makes GitHub notify Jenkins on every push so builds happen automatically.

### Step 1 — Expose Jenkins with ngrok

GitHub cannot reach `localhost:8090`, so you need ngrok to create a public URL for your local Jenkins.

In a **dedicated terminal tab** (keep it running):
```bash
ngrok http 8090
```

You will see output like:
```
Forwarding   https://abc123.ngrok-free.app -> http://localhost:8090
```

Copy the `https://...ngrok-free.app` URL.

> Every time you restart ngrok you get a new URL. Update the GitHub webhook whenever this happens.

### Step 2 — Add the Webhook in GitHub

In your GitHub repository: **Settings → Webhooks → Add webhook**

| Field | Value |
|-------|-------|
| Payload URL | `https://abc123.ngrok-free.app/github-webhook/` |
| Content type | `application/json` |
| Which events | **Just the push event** |

Click **Add webhook**.

> **The trailing slash matters.** Jenkins requires the URL to end with `/github-webhook/`. Without it you will get a `403 Forbidden` response.

### Step 3 — Verify the Webhook

Push any commit to your repository. In the ngrok terminal you should see:

```
POST /github-webhook/    200 OK
```

A `403` means the URL is wrong (missing `/github-webhook/`). A `200` means Jenkins received the payload.

### Step 4 — Enable the Trigger in the Jenkinsfile

The `triggers { githubPush() }` block in the Jenkinsfile (see Section 5) registers the webhook trigger. Jenkins processes this on the first build — after that, every push automatically starts a new build for the pushed branch.

---

## 8. GitHub Status Reporting

For Pull Requests to show a Jenkins status check (green ✓ or red ✗), Jenkins must call the GitHub Status API after each build. This is handled by the `post { success / failure }` block in the Jenkinsfile using `curl`.

The `github-token` credential and GitHub Server configured in Section 3.3 are what enable this. The `context` field in the curl payload (`"Jenkins CI"`) is the name that appears on the PR and in branch protection rules.

**What the PR looks like when working correctly:**

```
Some checks haven't completed yet
● Jenkins CI — Expected — Waiting for status to be reported   [Required]
[Merge pull request] — disabled until Jenkins reports green
```

Once Jenkins builds and the `post` block runs, the status updates to:
```
✓ All checks have passed
✓ Jenkins CI — Build passed
[Merge pull request] — enabled
```

---

## 9. Branch Protection

Branch protection prevents developers from pushing broken code directly to `main`. Every change must go through a Pull Request, and Jenkins must report green before the merge button activates.

**The protected workflow:**
```
feature branch → push → Jenkins builds → green? → open PR → merge to main
                                        → red?   → fix the code first
```

### Step 1 — Create a Ruleset

In your GitHub repository: **Settings → Rules → Rulesets → New ruleset → New branch ruleset**

| Field | Value |
|-------|-------|
| Ruleset Name | `main-protection` |
| Enforcement status | **Active** |

> **Enforcement status must be Active.** If left as "Disabled" the ruleset is saved but not applied.

### Step 2 — Set the Target Branch

Under **Target branches** → click **Add target** → **Include by pattern** → type `main` → click **Add**.

This tells the ruleset to apply only to the `main` branch.

### Step 3 — Configure Branch Rules

Enable the following:

| Rule | Purpose |
|------|---------|
| **Require a pull request before merging** | Nobody can push directly to `main` |
| **Require status checks to pass** | Jenkins must report green before merging |
| **Require branches to be up to date before merging** | The PR must include the latest `main` commits |
| **Block force pushes** | Prevents history rewriting on `main` |

### Step 4 — Add Jenkins as a Required Status Check

Under **Require status checks to pass** → click **Add checks** → in the search box type exactly:

```
Jenkins CI
```

Select it and confirm.

> **If Jenkins CI does not appear in the dropdown**, type the name manually and press Enter — GitHub accepts custom check names even without autocomplete. This works as long as at least one build has run and reported the `Jenkins CI` status to the repo.

### Step 5 — Save

Click **Create** (or **Save changes**). The ruleset is now active.

### The New Development Workflow

```bash
# Create a feature branch — never commit directly to main
git checkout -b feature/my-change

# Make your changes and commit
git add .
git commit -m "add my change"
git push origin feature/my-change
```

Jenkins automatically builds `feature/my-change` and posts a status to the commit. Open a Pull Request on GitHub — the merge button stays locked until Jenkins reports green.

**What happens if you try to push directly to `main`:**
```
remote: error: GH006: Protected branch update failed for refs/heads/main.
fatal: unable to access 'https://github.com/...': The requested URL returned error: 403
```

---

## 10. Jenkins Value in Practice

### 10.1 Behavior When a Test Fails

To see Jenkins catch a regression, deliberately break a test. In `TodoServiceTest.java`, find any assertion and flip it (e.g. change `isFalse()` to `isTrue()`), commit, and push to a feature branch.

**What Jenkins does:**

1. **Stops at the Test stage** — the stage turns red in the Stage View
2. **Marks the build FAILED** — posts a `failure` status to GitHub
3. **PR merge button stays locked** — GitHub blocks the merge
4. **Publishes the JUnit report** — shows exactly which test failed and the assertion message

**Revert the change, push again** — Jenkins builds green, status updates, merge button unlocks.

### 10.2 How Jenkins Improves the Development Workflow

| Without Jenkins | With Jenkins |
|----------------|--------------|
| Tests run only if the developer remembers | Tests run on every push, automatically |
| Broken code can be merged directly | Branch protection blocks merges until green |
| "Works on my machine" failures | Builds run in a clean, consistent environment |
| Bugs found during code review or in staging | Failed tests block the pipeline before bad code is merged |
| Artifacts are built locally, often untraceable | Every artifact is archived and linked to a commit |

---

## 11. Enhancement: Docker Image Build

Build and tag a Docker image from the pipeline. This requires the Docker socket to be mounted in the Jenkins container (already configured in `jenkins/docker-compose.yml`).

**Install Docker CLI in the running Jenkins container (one-time):**
```bash
docker exec -it -u root jenkins bash -c "
  apt-get update &&
  apt-get install -y docker.io &&
  docker --version
"
```

**Add to the Jenkinsfile after the Archive stage:**
```groovy
stage('Docker Build') {
    steps {
        sh "docker build -t ${APP_NAME}:${BUILD_NUMBER} ."
        sh "docker tag ${APP_NAME}:${BUILD_NUMBER} ${APP_NAME}:latest"
        echo "Image ${APP_NAME}:${BUILD_NUMBER} is ready"
    }
}
```

**Push to Docker Hub (requires credentials):**
```groovy
stage('Docker Push') {
    steps {
        withCredentials([usernamePassword(
            credentialsId: 'dockerhub-creds',
            usernameVariable: 'DOCKER_USER',
            passwordVariable: 'DOCKER_PASS'
        )]) {
            sh "echo ${DOCKER_PASS} | docker login -u ${DOCKER_USER} --password-stdin"
            sh "docker push ${APP_NAME}:${BUILD_NUMBER}"
        }
    }
}
```

Add `dockerhub-creds` to Jenkins credentials first (Kind: "Username with password", your Docker Hub account).

---

## 12. Troubleshooting

### Jenkins container will not start

```bash
docker compose -f jenkins/docker-compose.yml logs jenkins
```

**Port already in use:**
```
Error: Bind for 0.0.0.0:8090 failed: port is already allocated
```
Change the host port in `jenkins/docker-compose.yml`:
```yaml
ports:
  - "8095:8080"
```
Then access Jenkins at `http://localhost:8095`.

### Webhook returns 403 Forbidden

The payload URL is missing the `/github-webhook/` path or the trailing slash. Update the webhook in GitHub to:
```
https://your-ngrok-url.ngrok-free.app/github-webhook/
```

### Jenkins receives webhook but does not trigger a build

The `triggers { githubPush() }` block must be present in the Jenkinsfile and at least one build must have run to register the trigger. Trigger one build manually via **Build Now**, then subsequent pushes will fire automatically.

### Jenkins CI status does not appear in GitHub PR

1. Check the Jenkins build console output — look for the `curl` command near the end and confirm it returned a `201` response
2. Verify the `github-token` credential exists in Jenkins and has `repo:status` scope
3. Verify the GitHub Server is configured under **Manage Jenkins → System → GitHub** and **Test connection** succeeds

### `./mvnw: Permission denied`

```bash
git update-index --chmod=+x mvnw
git commit -m "restore mvnw execute permission"
git push
```

The Jenkinsfile already includes `sh 'chmod +x mvnw'` as a safety net.

### `java: command not found` or wrong Java version

1. Go to **Manage Jenkins → Tools → JDK installations**
2. Confirm the name is exactly `JDK-17`
3. Save and re-run the build

### Branch CI status check shows "No results" in branch protection search

GitHub only lists checks that have already been reported to the repository. If no build has run yet, type `Jenkins CI` directly into the search box and press Enter — GitHub accepts manually typed check names.

### Volume data lost after restarting Jenkins

Never use `docker compose down -v` — the `-v` flag deletes volumes. Always use:
```bash
docker compose -f jenkins/docker-compose.yml down
```

---

## Quick Reference

```bash
# Start Jenkins
docker compose -f jenkins/docker-compose.yml up -d

# Get the initial admin password
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword

# Open Jenkins in the browser
open http://localhost:8090          # macOS
xdg-open http://localhost:8090      # Linux

# Expose Jenkins to GitHub (run in a separate terminal)
ngrok http 8090

# Tail Jenkins logs
docker compose -f jenkins/docker-compose.yml logs -f jenkins

# Stop Jenkins (data preserved)
docker compose -f jenkins/docker-compose.yml down

# Create a feature branch and push
git checkout -b feature/my-change
git add . && git commit -m "my change"
git push origin feature/my-change
```
