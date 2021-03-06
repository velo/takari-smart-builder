package io.takari.maven.builder.smart;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.project.MavenProject;

/**
 * Reactor build queue manages reactor modules that are waiting for their upstream dependencies
 * build to finish.
 */
class ReactorBuildQueue {

  private final ProjectDependencyGraph dependencyGraph;

  private final Set<MavenProject> rootProjects;

  private final Set<MavenProject> projects;

  /**
   * Projects waiting for other projects to finish
   */
  private final Set<MavenProject> blockedProjects = new HashSet<MavenProject>();

  private final Set<MavenProject> finishedProjects = new HashSet<MavenProject>();

  public ReactorBuildQueue(Collection<MavenProject> projects,
      ProjectDependencyGraph dependencyGraph) {
    this.dependencyGraph = dependencyGraph;

    final Set<MavenProject> rootProjects = new HashSet<MavenProject>();

    for (MavenProject project : projects) {
      if (dependencyGraph.getUpstreamProjects(project, false).isEmpty()) {
        rootProjects.add(project);
      } else {
        blockedProjects.add(project);
      }
    }

    this.rootProjects = new LinkedHashSet<>(rootProjects);
    this.projects = new LinkedHashSet<>(projects);
  }

  /**
   * Marks specified project as finished building. Returns, possible empty, set of project's
   * downstream dependencies that become ready to build.
   */
  public Set<MavenProject> onProjectFinish(MavenProject project) {
    finishedProjects.add(project);
    Set<MavenProject> downstreamProjects = new HashSet<MavenProject>();
    for (MavenProject successor : getDownstreamProjects(project)) {
      if (blockedProjects.contains(successor) && isProjectReady(successor)) {
        blockedProjects.remove(successor);
        downstreamProjects.add(successor);
      }
    }
    return downstreamProjects;
  }

  public List<MavenProject> getDownstreamProjects(MavenProject project) {
    return dependencyGraph.getDownstreamProjects(project, false);
  }

  private boolean isProjectReady(MavenProject project) {
    for (MavenProject upstream : dependencyGraph.getUpstreamProjects(project, false)) {
      if (!finishedProjects.contains(upstream)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns {@code true} when no more projects are left to schedule.
   */
  public boolean isEmpty() {
    return blockedProjects.isEmpty();
  }

  /**
   * Returns reactor build root projects, that is, projects that do not have upstream dependencies.
   */
  public Set<MavenProject> getRootProjects() {
    return rootProjects;
  }

  public int getBlockedCount() {
    return blockedProjects.size();
  }

  public int getFinishedCount() {
    return finishedProjects.size();
  }

  public int getReadyCount() {
    return projects.size() - blockedProjects.size() - finishedProjects.size();
  }

  public Set<MavenProject> getReadyProjects() {
    Set<MavenProject> projects = new LinkedHashSet<>(this.projects);
    projects.removeAll(blockedProjects);
    projects.removeAll(finishedProjects);
    return projects;
  }

}
