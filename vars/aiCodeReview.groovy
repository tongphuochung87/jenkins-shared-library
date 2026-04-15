def call(Map config = [:]) {
    // Required params
    def gitlabProjectPath = config.gitlabProjectPath
        ?: error("'gitlabProjectPath' is required")

    // Credential ID for Copilot token — default to org-wide, override per project
    def copilotCredentialId = config.copilotCredentialId ?: 'github-copilot-token'

    pipeline {
        agent { label 'AICodeReview' }

        environment {
            GLAB_TOKEN           = credentials('gitlab-token')
            COPILOT_GITHUB_TOKEN = credentials("${copilotCredentialId}")
            GITLAB_HOST          = 'https://gitlab.com'
            GITLAB_PROJECT_PATH  = "${gitlabProjectPath}"
        }

        parameters {
            string(name: 'MR_IID', defaultValue: '', description: 'MR IID to review')
        }

        stages {
            stage('Verify Tools') {
                steps {
                    sh '''
                        copilot --version
                        glab --version
                    '''
                }
            }

            stage('Authenticate') {
                steps {
                    sh '''
                        glab auth login \
                            --hostname "${GITLAB_HOST}" \
                            --token    "${GLAB_TOKEN}"

                        echo "${COPILOT_GITHUB_TOKEN}" | gh auth login --with-token
                    '''
                }
            }

            stage('Resolve MR') {
                steps {
                    script {
                        def mrIid = params.MR_IID?.trim() ?: env.gitlabMergeRequestIid ?: ''
                        if (!mrIid) {
                            error("Could not determine MR IID. Pass MR_IID parameter or trigger via GitLab webhook.")
                        }
                        env.MR_IID = mrIid
                    }
                }
            }

            stage('Fetch MR Data') {
                steps {
                    sh '''
                        glab mr view "${MR_IID}" --repo "${GITLAB_PROJECT_PATH}" > mr_meta.txt
                        glab mr diff "${MR_IID}" --repo "${GITLAB_PROJECT_PATH}" > mr_diff.txt

                        LINES=$(wc -l < mr_diff.txt)
                        if [ "$LINES" -gt 800 ]; then
                            head -n 800 mr_diff.txt > mr_diff_truncated.txt
                            mv mr_diff_truncated.txt mr_diff.txt
                            echo "\\n[... diff truncated ...]" >> mr_diff.txt
                        fi
                    '''
                }
            }

            stage('AI Code Review') {
                steps {
                    sh '''
                        MR_META=$(cat mr_meta.txt)
                        MR_DIFF=$(cat mr_diff.txt)

                        PROMPT="You are a senior software engineer performing a code review.

Here is the merge request metadata:
${MR_META}

Here is the diff:
${MR_DIFF}

Please provide a structured review:
1. **Summary** – What does this MR do?
2. **Changes** – Key files and logic changed
3. **Issues** – Bugs, security concerns, logic errors
4. **Suggestions** – Non-blocking improvements
5. **Verdict** – LGTM / Needs Changes / Needs Discussion"

                        copilot --prompt "$PROMPT" --silent --yolo > review.txt
                        cat review.txt
                    '''
                }
            }

            stage('Post Comment') {
                steps {
                    sh '''
                        {
                          printf '## 🤖 Copilot AI Code Review\n\n'
                          printf '_Automated review by GitHub Copilot via Jenkins._\n\n---\n'
                          cat review.txt
                          printf '\n\n---\n_Re-run: trigger the **mr-ai-review** job._\n'
                        } > comment.txt

                        glab mr note "${MR_IID}" \
                            --repo    "${GITLAB_PROJECT_PATH}" \
                            --message "$(cat comment.txt)"
                    '''
                }
            }
        }

        post {
            failure {
                sh """
                    printf '## 🤖 Copilot Review — Failed\n\nCheck [Jenkins build](${env.BUILD_URL}).\n' > fail_comment.txt
                    glab mr note "${env.MR_IID}" \
                        --repo "${env.GITLAB_PROJECT_PATH}" \
                        --message "\$(cat fail_comment.txt)" || true
                """
            }
            always {
                sh 'rm -f mr_meta.txt mr_diff.txt review.txt comment.txt fail_comment.txt || true'
                cleanWs()
            }
        }
    }
}
