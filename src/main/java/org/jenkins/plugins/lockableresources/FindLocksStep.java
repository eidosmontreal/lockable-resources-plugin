package org.jenkins.plugins.lockableresources;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class FindLocksStep extends Step implements Serializable {

  private static final Logger LOGGER = Logger.getLogger(FindLocksStepExecution.class.getName());
  private static final long serialVersionUID = 148049840628540827L;
  private static final String ANY_BUILD = "any";

  @CheckForNull
  public String anyOfLabels = null;

  @CheckForNull
  public String noneOfLabels = null;

  @CheckForNull
  public String allOfLabels = null;

  @CheckForNull
  public String matching = null;

  @CheckForNull
  public String build = null;

  @DataBoundSetter
  public void setAnyOfLabels(String anyOfLabels) {
    if (StringUtils.isNotBlank(anyOfLabels)) {
      this.anyOfLabels = anyOfLabels;
    }
  }

  @DataBoundSetter
  public void setNoneOfLabels(String noneOfLabels) {
    if (StringUtils.isNotBlank(noneOfLabels)) {
      this.noneOfLabels = noneOfLabels;
    }
  }

  @DataBoundSetter
  public void setAllOfLabels(String allOfLabels) {
    if (StringUtils.isNotBlank(allOfLabels)) {
      this.allOfLabels = allOfLabels;
    }
  }

  @DataBoundSetter
  public void setMatching(String matching) {
    if (StringUtils.isNotBlank(matching)) {
      this.matching = matching;
    }
  }

  @DataBoundSetter
  public void setBuild(String build) {
    if (StringUtils.isNotBlank(build)) {
      this.build = build;
    }
  }

  @DataBoundConstructor
  public FindLocksStep() {
  }

  public Predicate<LockableResource> asPredicate(StepContext context) {
    String currentJobName = null;
    try {
      Run run = context.get(Run.class);
      currentJobName = run.getExternalizableId();
    } catch (Exception e) {
      LOGGER.warning("Failed to resolve the current job");
    }
    final String finalCurrentJobName = currentJobName;
    return lockableResource -> {
      if (StringUtils.isNotBlank(anyOfLabels)) {
        List<String> anyLabelsList = Arrays.asList(anyOfLabels.split("\\s+"));
        List<String> resourceLabels = Arrays.asList(lockableResource.getLabels().split("\\s+"));
        if (anyLabelsList.stream().noneMatch(l -> resourceLabels.contains(l)))
          return false;
      }
      if (StringUtils.isNotBlank(noneOfLabels)) {
        List<String> noneOfLabelsList = Arrays.asList(noneOfLabels.split("\\s+"));
        List<String> resourceLabels = Arrays.asList(lockableResource.getLabels().split("\\s+"));
        if (noneOfLabelsList.stream().anyMatch(l -> resourceLabels.contains(l)))
          return false;
      }
      if (StringUtils.isNotBlank(allOfLabels)) {
        List<String> allOfLabelsList = Arrays.asList(allOfLabels.split("\\s+"));
        List<String> resourceLabels = Arrays.asList(lockableResource.getLabels().split("\\s+"));
        if (allOfLabelsList.stream().allMatch(l -> resourceLabels.contains(l)) == false)
          return false;
      }
      if (StringUtils.isNotBlank(matching)) {
        if (lockableResource.getName().matches(matching) == false) {
          return false;
        }
      }
      if (StringUtils.isNotBlank(build)) {
        if (build.equals(ANY_BUILD) == false) {
          if (StringUtils.isBlank(lockableResource.getBuildId())) {
            // ignore unlocked resources
            return false;
          }
          if (lockableResource.getBuildId().equals(build) == false) {
            return false;
          }
        }
      }
      else {
        // when build is blank, we restrict the search on the current build
        if (finalCurrentJobName == null) {
          // if we are not part of a job, filter out everything
          return false;
        }
        if (StringUtils.isBlank(lockableResource.getBuildId())) {
          // ignore unlocked resources
          return false;
        }
        if (lockableResource.getBuildId().equals(finalCurrentJobName) == false) {
          // ignore resources locked by another job
          return false;
        }
      }
      return true;
    };
  }

  @Extension
  public static final class DescriptorImpl extends StepDescriptor {

    @Override
    public String getFunctionName() {
      return "findLocks";
    }

    @NonNull
    @Override
    public String getDisplayName() {
      return "Find existing shared resource";
    }

    @Override
    public boolean takesImplicitBlockArgument() {
      return false;
    }

    @Override
    public Set<Class<?>> getRequiredContext() {
      return Collections.singleton(TaskListener.class);
    }
  }

  @Override
  public String toString() {
    List<String> desc = new ArrayList<>();
    if (StringUtils.isNotBlank(anyOfLabels)) {
      desc.add("anyLabels:" + anyOfLabels);
    }
    if (StringUtils.isNotBlank(allOfLabels)) {
      desc.add("allOfLabels:" + allOfLabels);
    }
    if (StringUtils.isNotBlank(noneOfLabels)) {
      desc.add("noneOfLabels:" + noneOfLabels);
    }
    if (StringUtils.isNotBlank(matching)) {
      desc.add("matching:" + matching);
    }
    if (StringUtils.isNotBlank(build)) {
      desc.add("build:" + build);
    }
    if (desc.isEmpty()) {
      return "all locked by current build";
    }
    else {
      return desc.stream().collect(Collectors.joining("; "));
    }
  }

  @Override
  public StepExecution start(StepContext context) throws Exception {
    return new FindLocksStepExecution(this, context);
  }
}
