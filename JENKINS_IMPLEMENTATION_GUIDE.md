# Jenkins Implementation Guide for `template-jenkins`

This guide sets up a local Jenkins controller for this repository and connects it to GitHub. When finished, Jenkins will:

- discover repository branches;
- run the Maven build and all unit tests on every push;
- archive the executable Spring Boot JAR;
- publish a `Jenkins CI` commit status to GitHub; and
- allow GitHub branch protection to require a successful Jenkins build.

The implementation uses the files already included in this repository:

- `Jenkinsfile` defines the CI pipeline;
- `jenkins/docker-compose.yml` runs Jenkins locally;
- `mvnw` provides the project's Maven version; and
- `pom.xml` defines the Java 17 Spring Boot build.

> This setup is intended for learning and local development. An ngrok tunnel exposes the Jenkins web interface as well as its webhook endpoint. Do not treat this configuration as a production deployment.

## 1. Prerequisites

Install the following tools on the machine that will run Jenkins:

| Tool | Purpose |
|---|---|
| Docker Desktop or Docker Engine | Runs the Jenkins controller |
| Docker Compose v2 | Starts Jenkins from the repository configuration |
| Git | Creates and pushes branches |
| ngrok | Makes a local Jenkins webhook reachable from GitHub |
| A GitHub account with repository administration access | Creates the token, webhook, and branch rules |

Verify the local tools:

```bash
docker --version
docker compose version
git --version
ngrok version
```

Clone the repository if it is not already available:

```bash
git clone https://github.com/Brianmax/template-jenkins.git
cd template-jenkins
```

## 2. Understand the implementation

The resulting flow is:

```text
Git push
   |
   v
GitHub webhook
   |
   v
ngrok tunnel -> Jenkins multibranch job
                    |
                    v
              compile -> test -> package -> archive
                    |
                    v
            GitHub commit status: Jenkins CI
```

The setup expects three Jenkins resources:

1. A JDK installation named exactly `JDK-17`.
2. A Secret Text credential named exactly `github-token`.
3. A Username with password credential named exactly `github-scan` for authenticated branch and pull-request discovery.

The following sections create all three resources.

## 3. Start Jenkins

From the repository root, validate and start the Jenkins Compose project:

```bash
docker compose -f jenkins/docker-compose.yml config
docker compose -f jenkins/docker-compose.yml up -d
```

Jenkins is exposed at <http://localhost:8090>. Port 8090 avoids a conflict with the Todos API, which uses port 8080.

Follow the startup logs until Jenkins is ready:

```bash
docker compose -f jenkins/docker-compose.yml logs -f jenkins
```

Press `Ctrl+C` after the log contains `Jenkins is fully up and running`.

Retrieve the initial administrator password:

```bash
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

Open <http://localhost:8090>, enter the password, and then:

1. Select **Install suggested plugins**.
2. Create the first administrator account.
3. Keep the Jenkins URL as `http://localhost:8090/` for now.
4. Select **Start using Jenkins**.

Jenkins data is stored in the named `jenkins_home` volume and survives ordinary container restarts.

## 4. Install the required plugins

Go to **Manage Jenkins -> Plugins -> Available plugins** and install these plugins if they are not already present:

| Plugin | Reason |
|---|---|
| Pipeline | Executes the `Jenkinsfile` |
| Git | Checks out repository commits |
| GitHub | Receives `/github-webhook/` push notifications |
| GitHub Branch Source | Discovers GitHub branches and pull requests |
| Credentials Binding | Makes `github-token` available without printing it |
| JUnit | Publishes Maven test results |
| Pipeline: Stage View | Displays the pipeline stages |

Restart Jenkins if the plugin manager requests it.

## 5. Configure Java 17

The Jenkins container already includes Java 17, but the pipeline refers to it by the configured tool name `JDK-17`.

First, confirm the container's Java installation:

```bash
docker exec jenkins sh -c 'echo "$JAVA_HOME" && java -version'
```

Then go to **Manage Jenkins -> Tools -> JDK installations -> Add JDK** and configure:

| Field | Value |
|---|---|
| Name | `JDK-17` |
| Install automatically | Unchecked |
| JAVA_HOME | The path printed by the previous command, normally `/opt/java/openjdk` |

Save the configuration.

The name is case-sensitive and must match `jdk 'JDK-17'` in the repository's `Jenkinsfile`.

## 6. Create GitHub credentials for scanning and commit statuses

The pipeline calls GitHub's commit-status API after each build. Create a repository-scoped fine-grained personal access token:

1. In GitHub, open **Settings -> Developer settings -> Personal access tokens -> Fine-grained tokens**.
2. Select **Generate new token**.
3. Give the token a descriptive name such as `template-jenkins-status`.
4. Select the owner of `Brianmax/template-jenkins` as the resource owner.
5. Under **Repository access**, select only `template-jenkins`.
6. Under **Repository permissions**, set **Contents** and **Pull requests** to **Read-only**, and **Commit statuses** to **Read and write**.
7. Generate and copy the token.

The GitHub account that owns the token must have permission to push to the repository.

Store the token in Jenkins:

1. Open **Manage Jenkins -> Credentials**.
2. Select **System -> Global credentials -> Add Credentials**.
3. Set **Kind** to **Secret text**.
4. Paste the token into **Secret**.
5. Set **ID** to exactly `github-token`.
6. Set **Description** to `GitHub commit status token`.
7. Select **Create**.

Store the same token a second time in the credential form required by GitHub Branch Source:

1. Select **Add Credentials** again.
2. Set **Kind** to **Username with password**.
3. Set **Username** to the GitHub account name that owns the token.
4. Paste the token into **Password**.
5. Set **ID** to exactly `github-scan`.
6. Set **Description** to `Authenticated GitHub branch scanning`.
7. Select **Create**.

Authenticated scanning avoids GitHub's anonymous API limit of 60 requests per hour. The Secret Text form remains necessary because the `Jenkinsfile` binds `github-token` when publishing commit statuses.

Do not put the token in the repository, a shell script, the Compose file, or the Jenkinsfile.

> A classic personal access token with the narrow `repo:status` scope can also be used. Prefer a fine-grained token restricted to this repository.

## 7. Create the multibranch pipeline

From the Jenkins dashboard:

1. Select **New Item**.
2. Enter `template-jenkins`.
3. Select **Multibranch Pipeline**.
4. Select **OK**.

Under **Branch Sources**:

1. Select **Add source -> GitHub**.
2. Set **Repository HTTPS URL** to:

   ```text
   https://github.com/Brianmax/template-jenkins.git
   ```

3. Validate the URL.
4. Set **Credentials** to `github-scan`. Although anonymous scanning works for a public repository, its 60-request-per-hour API limit can make Jenkins delay branch indexing for several minutes.
5. Under **Behaviors**, enable branch discovery. Enable pull-request discovery if pull requests, including fork pull requests, must have their own Jenkins jobs.

Under **Build Configuration**, keep:

| Field | Value |
|---|---|
| Mode | `by Jenkinsfile` |
| Script Path | `Jenkinsfile` |

Under **Scan Multibranch Pipeline Triggers**, enable **Periodically if not otherwise run** and choose a moderate interval such as one hour. This is a fallback for webhook outages and newly created branches.

Select **Save**. Jenkins scans the repository, discovers `main`, reads its `Jenkinsfile`, and starts the first build.

## 8. Verify the first build

Open the `main` branch job and then its latest build. The stage view should contain:

```text
Checkout -> Build -> Test -> Package -> Archive Artifacts
```

Expected results:

- Maven compiles the Java 17 project.
- All 14 unit tests pass.
- Jenkins publishes the Surefire XML results.
- Jenkins archives `target/todos-api-0.0.1-SNAPSHOT.jar`.
- GitHub receives the `Jenkins CI` status for the commit.

Use **Console Output** to diagnose a failed stage. Use **Test Result** to inspect test failures and **Artifacts** to download the packaged JAR.

Do not continue to branch protection until at least one successful `Jenkins CI` status appears on GitHub.

## 9. Add the webhook

GitHub cannot send a webhook to `localhost`. Start an authenticated ngrok tunnel in a dedicated terminal:

```bash
ngrok http 8090
```

Copy the public HTTPS URL, for example:

```text
https://example-subdomain.ngrok-free.app
```

In GitHub, open **Brianmax/template-jenkins -> Settings -> Webhooks -> Add webhook** and enter:

| Field | Value |
|---|---|
| Payload URL | `https://example-subdomain.ngrok-free.app/github-webhook/` |
| Content type | `application/json` |
| Secret | A new random webhook secret, if the installed Jenkins plugin configuration supports it |
| SSL verification | Enabled |
| Events | Just the push event |
| Active | Enabled |

The `/github-webhook/` path and trailing slash are required.

After saving, inspect the webhook's **Recent Deliveries**. A successful ping or push normally receives an HTTP 200 response.

> The tunnel exposes the Jenkins login page to the internet. Use a strong administrator password, keep Jenkins updated, leave anonymous access disabled, and stop ngrok when testing is complete.

## 10. Test automatic builds

Create and push a feature branch:

```bash
git switch -c feature/verify-jenkins
git commit --allow-empty -m "verify Jenkins webhook"
git push -u origin feature/verify-jenkins
```

Confirm all of the following:

1. GitHub records a successful webhook delivery.
2. Jenkins discovers or updates `feature/verify-jenkins`.
3. The branch pipeline runs automatically.
4. The build finishes successfully.
5. GitHub shows the `Jenkins CI` status on the pushed commit.

If the branch is not discovered immediately, select **Scan Multibranch Pipeline Now** once. The periodic scan configured earlier provides an additional fallback.

## 11. Configure branch protection

Configure protection only after GitHub has received a successful status named `Jenkins CI` during the previous seven days.

In GitHub:

1. Open **Settings -> Rules -> Rulesets**.
2. Select **New ruleset -> New branch ruleset**.
3. Name it `main-protection`.
4. Set enforcement to **Active**.
5. Target the `main` branch.
6. Enable **Require a pull request before merging**.
7. Enable **Require status checks to pass**.
8. Add `Jenkins CI` as the required status check.
9. Enable **Require branches to be up to date before merging** if that matches the team's merge policy.
10. Enable **Block force pushes**.
11. Save the ruleset.

Repository rules available for private repositories depend on the GitHub account and organization plan.

Test the protected workflow:

```text
feature branch -> push -> Jenkins build -> open pull request -> Jenkins CI passes -> merge
```

Each new commit on the pull request must receive a successful status. A status attached to an older commit does not satisfy protection for the latest commit.

## 12. Prove that Jenkins blocks regressions

On a feature branch, temporarily change a passing assertion in `TodoServiceTest` so it fails, then commit and push it.

Jenkins should:

1. mark the **Test** stage as failed;
2. publish the JUnit failure details;
3. skip packaging and artifact archival;
4. publish a failed `Jenkins CI` status; and
5. prevent the pull request from merging when the status is required.

Revert the intentional failure and push again. The new build should pass and update the latest commit status.

## 13. Run the Todos API separately

Jenkins does not need the application database for the current Mockito unit tests. To run the complete API separately:

```bash
docker compose up --build -d
docker compose ps
```

Create and retrieve a todo:

```bash
curl -i -X POST http://localhost:8080/api/todos \
  -H 'Content-Type: application/json' \
  -d '{"title":"Verify Jenkins","description":"Run the complete CI workflow"}'

curl -i http://localhost:8080/api/todos
```

Stop the API and PostgreSQL without deleting database data:

```bash
docker compose down
```

The API Compose project and Jenkins Compose project use separate networks and volumes.

## 14. Operating Jenkins

Start Jenkins:

```bash
docker compose -f jenkins/docker-compose.yml up -d
```

Check its status:

```bash
docker compose -f jenkins/docker-compose.yml ps
```

Follow logs:

```bash
docker compose -f jenkins/docker-compose.yml logs -f jenkins
```

Restart Jenkins:

```bash
docker compose -f jenkins/docker-compose.yml restart jenkins
```

Stop Jenkins while retaining configuration and build history:

```bash
docker compose -f jenkins/docker-compose.yml down
```

Do not add `-v` unless permanently deleting the Jenkins home volume is intentional.

## 15. Troubleshooting

### `JDK-17` is not configured

Symptoms include `Tool type "jdk" does not have an install of "JDK-17" configured`.

1. Run `docker exec jenkins sh -c 'echo "$JAVA_HOME" && java -version'`.
2. Return to **Manage Jenkins -> Tools**.
3. Confirm that the JDK name is exactly `JDK-17`.
4. Confirm that JAVA_HOME matches the path inside the container.

### Jenkins cannot find `github-token`

Create a global **Secret text** credential with ID exactly `github-token`. Credential names and IDs are case-sensitive.

### Branch indexing pauses because of the GitHub API quota

If the scan log reports anonymous access, a quota of 60 requests, or says Jenkins is sleeping before continuing:

1. Create the global **Username with password** credential `github-scan` as described in section 6.
2. Open the multibranch job and select **Configure**.
3. Under **Branch Sources -> GitHub**, select `github-scan` in **Credentials**.
4. Save and select **Scan Multibranch Pipeline Now**.

### Tests pass but GitHub has no status

1. Confirm that the token owner has push access to the repository.
2. Confirm **Commit statuses: Read and write** on the fine-grained token.
3. Confirm that the token is authorized for `Brianmax/template-jenkins`.
4. Confirm that the Jenkins credential ID is `github-token`.
5. Inspect the end of the Jenkins console output for the GitHub API response.
6. Confirm the Jenkins job uses the HTTPS repository URL shown in this guide.

The current `Jenkinsfile` derives `owner/repository` from an HTTPS GitHub URL. An SSH URL such as `git@github.com:Brianmax/template-jenkins.git` is not compatible with that parsing expression.

### Webhook succeeds but no branch build starts

1. Verify that the GitHub plugin is installed.
2. Confirm `/github-webhook/` and its trailing slash in the payload URL.
3. Confirm `triggers { githubPush() }` exists in the Jenkinsfile.
4. Select **Scan Multibranch Pipeline Now**.
5. Check the GitHub webhook's **Recent Deliveries** payload and response.
6. Check **Manage Jenkins -> System Log** for GitHub hook messages.

### A new branch is not visible

Run **Scan Multibranch Pipeline Now** and verify that branch discovery is enabled under the GitHub branch-source behaviors. Keep periodic scanning enabled as a fallback.

### GitHub does not offer `Jenkins CI` as a required check

Run a successful pipeline first. GitHub requires a status check to have completed successfully in the repository recently before it can be selected reliably as a required check.

### `./mvnw: Permission denied`

The Jenkinsfile runs `chmod +x mvnw`, but the repository should also preserve its executable bit:

```bash
git update-index --chmod=+x mvnw
git commit -m "restore Maven wrapper executable bit"
git push
```

### Jenkins port 8090 is already used

Change the host side of the mapping in `jenkins/docker-compose.yml`, for example:

```yaml
ports:
  - "8095:8080"
```

Restart the Compose project and update both the browser URL and ngrok command.

## 16. Production hardening checklist

Before using Jenkins beyond a local exercise:

- run build agents separately instead of executing builds on the controller;
- use TLS with a stable DNS name instead of a temporary tunnel;
- restrict network access to the Jenkins UI;
- configure webhook signature verification;
- use GitHub App credentials or narrowly scoped, expiring tokens;
- pin and routinely update Jenkins and plugin versions;
- back up `JENKINS_HOME` and test restoration;
- add controller and agent resource limits;
- avoid running Jenkins as root;
- do not mount the host Docker socket unless its host-level security impact is accepted; and
- add integration tests before treating the archived JAR as deployable.

## Completion checklist

- [ ] Jenkins opens at `http://localhost:8090`.
- [ ] Required plugins are installed.
- [ ] `JDK-17` points to the container's Java installation.
- [ ] The `github-token` Secret Text credential exists.
- [ ] The `github-scan` Username with password credential is selected for the GitHub branch source.
- [ ] The multibranch source uses `https://github.com/Brianmax/template-jenkins.git`.
- [ ] `main` builds successfully and archives the JAR.
- [ ] The webhook returns HTTP 200 for a push.
- [ ] A pushed feature branch builds automatically.
- [ ] GitHub displays `Jenkins CI` on the latest commit.
- [ ] The `main` ruleset requires `Jenkins CI`.
- [ ] A deliberately failing test blocks the pipeline and merge.
