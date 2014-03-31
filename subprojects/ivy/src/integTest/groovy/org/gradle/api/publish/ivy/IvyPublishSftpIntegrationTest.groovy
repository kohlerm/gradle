/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.publish.ivy

import org.apache.sshd.common.util.Buffer
import org.apache.sshd.server.sftp.SftpSubsystem
import org.gradle.test.fixtures.ivy.IvySftpRepository
import org.gradle.test.fixtures.server.sftp.SFTPServer
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Unroll

@Requires(TestPrecondition.JDK6_OR_LATER)
@Unroll
class IvyPublishSftpIntegrationTest extends AbstractIvyPublishIntegTest {

    @Rule
    final SFTPServer server = new SFTPServer(this)

    IvySftpRepository getIvySftpRepo(boolean m2Compatible = false, String dirPattern = null) {
        new IvySftpRepository(server, '/repo', m2Compatible, dirPattern)
    }

    def "can publish to a SFTP repository with layout #layout"() {
        given:
        def ivySftpRepo = getIvySftpRepo(m2Compatible)
        def module = ivySftpRepo.module("org.group.name", "publish", "2")

        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'

            version = '2'
            group = 'org.group.name'

            publishing {
                repositories {
                    ivy {
                        url "${ivySftpRepo.uri}"
                        credentials {
                            username 'sftp'
                            password 'sftp'
                        }
                        layout "$layout"
                    }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        succeeds 'publish'

        then:
        module.assertIvyAndJarFilePublished()
        module.jarFile.assertIsCopyOf(file('build/libs/publish-2.jar'))

        where:
        layout   | m2Compatible
        'gradle' | false
        'maven'  | true
    }

    def "can publish to a SFTP repository with pattern layout and m2Compatible: #m2Compatible"() {
        given:
        def ivySftpRepo = getIvySftpRepo(m2Compatible, "[module]/[organisation]/[revision]")
        def module = ivySftpRepo.module("org.group.name", "publish", "2")

        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'

            version = '2'
            group = 'org.group.name'

            publishing {
                repositories {
                    ivy {
                        url "${ivySftpRepo.uri}"
                        credentials {
                            username 'sftp'
                            password 'sftp'
                        }
                        layout "pattern", {
                            artifact "${ivySftpRepo.baseArtifactPattern}"
                            ivy "${ivySftpRepo.baseIvyPattern}"
                            m2compatible = $m2Compatible
                        }
                    }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        succeeds 'publish'

        then:
        module.assertIvyAndJarFilePublished()
        module.jarFile.assertIsCopyOf(file('build/libs/publish-2.jar'))

        where:
        m2Compatible << [true, false]
    }

    def "publishing to a SFTP repo when #action fails"(String action, int failingCommand) {
        given:
        server.sftpSubsystem = new SftpSubsystem() {
            @Override
            protected void process(Buffer buffer) throws IOException {
                int originalBufferPosition = buffer.rpos();
                buffer.getInt(); //length
                int type = buffer.getByte();
                int id = buffer.getInt();
                if (type == failingCommand) {
                    sendStatus(id, SSH_FX_FAILURE, "An error occurred");
                } else {
                    buffer.rpos(originalBufferPosition)
                    super.process(buffer)
                }
            }
        }

        and:
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'

            version = '2'
            group = 'org.group.name'

            publishing {
                repositories {
                    ivy {
                        url "${ivySftpRepo.uri}"
                        credentials {
                            username 'sftp'
                            password 'sftp'
                        }
                    }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        fails 'publish'

        then:
        failure.assertHasDescription("Execution failed for task ':publishIvyPublicationToIvyRepository'.")
                .assertHasCause("Failed to publish publication 'ivy' to repository 'ivy'")
                .assertHasCause("org.apache.sshd.common.SshException: SFTP error (4): An error occurred")

        where:
        action                  | failingCommand
        'directory creation'    | SftpSubsystem.SSH_FXP_MKDIR
        'writing file contents' | SftpSubsystem.SSH_FXP_WRITE
    }
}
