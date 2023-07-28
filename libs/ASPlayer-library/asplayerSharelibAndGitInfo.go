package asplayerSharelibAndGitInfo

import (
    "android/soong/android"
    "android/soong/java"
    "os/exec"
    "fmt"
    "strings"
    "strconv"
)

func asplayerSharelibAndGitInfoDefaults(ctx android.LoadHookContext) {
    sdkVersion := ctx.DeviceConfig().PlatformVndkVersion()
    sdkVersionInt, err := strconv.Atoi(sdkVersion)
    if err != nil {
        fmt.Printf("%v fail to convert", err)
    } else {
        fmt.Println("ASPlayer sdkVersion:", sdkVersionInt)
    }

    setversion(ctx)
}

func execCommand(cmd string, message string) ([]byte, error) {
    cmdOut, cmdErr := exec.Command("/bin/bash", "-c", cmd).CombinedOutput()
    if cmdErr != nil {
        fmt.Printf("ASPlayer %s failed %s\n", message, cmdErr)
    }
    return cmdOut, cmdErr
}

func replaceBuildConfigStringField(filePath string, fieldName string, newVal string) ([]byte, error) {
    // public static final String XX = "xx";
    cmd := fmt.Sprintf("sed -i -r 's/%s(.*)=(.*);/%s = \"%s\";/g' %s", fieldName, fieldName, newVal, filePath)
    cmdOut, cmdErr := exec.Command("/bin/bash", "-c", cmd).CombinedOutput()
    if cmdErr != nil {
        fmt.Printf("ASPlayer update field \"%s\" failed %s\n", fieldName, cmdErr)
    }
    return cmdOut, cmdErr
}

func replaceBuildConfigBoolField(filePath string, fieldName string, newVal bool) ([]byte, error) {
    var val string
    if newVal {
        val = "true"
    } else {
        val = "false"
    }

    cmd := fmt.Sprintf("sed -i -r 's/%s(.*)=(.*);/%s = %s;/g' %s", fieldName, fieldName, val, filePath)
    cmdOut, cmdErr := execCommand(cmd, fmt.Sprintf("update field %s", fieldName))
    return cmdOut, cmdErr
}

func setversion(ctx android.BaseContext) {
    var dir = string(ctx.Config().Getenv("PWD"))

    // sdkVersion := ctx.DeviceConfig().PlatformVndkVersion()

    sourceDir := dir + "/vendor/amlogic/common/ASPlayer"
    versionFile := sourceDir + "/version/VERSION"
    fmt.Println("ASPlayer versionFile: ", versionFile)

    javaBuildConfigTemplateFile := sourceDir + "/libs/ASPlayer-library/BuildConfiguration.java.in"
    javaBuildConfigFile := sourceDir + "/libs/ASPlayer-library/src/main/java/com/amlogic/asplayer/BuildConfiguration.java"

    // copy build config file
    copyConfigCmd := fmt.Sprintf("cp \"%s\" \"%s\"", javaBuildConfigTemplateFile, javaBuildConfigFile)
    execCommand(copyConfigCmd, "copy build config file")
    fmt.Printf("ASPlayer copy: %s => %s\n", javaBuildConfigTemplateFile, javaBuildConfigFile)

    majorVersionCmd := "sed -n 's/Major_V=\\(.*\\)/\\1/p' " + versionFile
    majorVersionout, _ := execCommand(majorVersionCmd, "get major version")
    majorVersion := strings.ReplaceAll(string(majorVersionout), "\n", "")
    fmt.Printf("ASPlayer majorVersion: %s\n", majorVersion)

    subVersionCmd := "sed -n 's/Minor_V=\\(.*\\)/\\1/p' " + versionFile
    subVersionout, _ := execCommand(subVersionCmd, "get sub version")
    subVersion := strings.ReplaceAll(string(subVersionout), "\n", "")
    fmt.Printf("ASPlayer subVersion: %s\n", subVersion)

    baseChangeIdCmd := "grep \"^BaseChangeId\" " + versionFile + " | awk -F [=] '{print $2}' | cut -c1-6"
    baseChangeIdout, _ := execCommand(baseChangeIdCmd, "get base change id")

    commitNumsCmd := "cd " + sourceDir + " && git log | grep \"Change-Id: \" | grep -n " + strings.ReplaceAll(string(baseChangeIdout), "\n", "") + " | awk -F \":\" '{printf \"%d\", $1-1}'"
    commitNumsout, _ := execCommand(commitNumsCmd, "get commit count")

    gitCommitIdCmd := "cd "+ sourceDir + " && git rev-parse --short HEAD"
    gitCommitIdout, _ := execCommand(gitCommitIdCmd, "get commit id")

    gitUncommitFileNumcmd := "cd " + sourceDir + " && git diff | grep +++ -c"
    gitUncommitFileNumout, _ := execCommand(gitUncommitFileNumcmd, "get uncommit file number")

    gitLastChangCmd := "cd " + sourceDir + " && git log | grep Date -m 1 | awk '$1=$1'"
    gitLastChangout, _ := execCommand(gitLastChangCmd, "get last changed")

    buildTimeout, _ := execCommand("date", "get build time")

    gitBranchNameCmd := "cd " + sourceDir + " && git branch -a | sed -n '/'*'/p'| awk '$1=$1'"
    gitBranchNameout, _ := execCommand(gitBranchNameCmd, "get git branch name")

    gitComitPdCmd := "cd " + sourceDir + " && git log | grep PD# -m 1 | awk '$1=$1'"
    gitComitPdout, _ := execCommand(gitComitPdCmd, "get commit pd")

    gitComitChandIdCmd := "cd " + sourceDir + " && git log | grep Change-Id -m 1 | awk '$1=$1'"
    gitComitChandIdout, _ := execCommand(gitComitChandIdCmd, "get commit changed id")

    //gitVersion := string(gitVersionout)
    commitNums := strings.ReplaceAll(string(commitNumsout), "\n", "")
    gitUncommitFileNum := strings.ReplaceAll(string(gitUncommitFileNumout), "\n", "")
    gitLastChang := strings.ReplaceAll(string(gitLastChangout), "\n", "")
    buildTime := strings.ReplaceAll(string(buildTimeout), "\n", "")
    buildName := strings.ReplaceAll(string(ctx.Config().Getenv("LOGNAME")), "\n", "")
    gitBranchName := strings.ReplaceAll(string(gitBranchNameout), "\n", "")
    gitCommitId := strings.ReplaceAll(string(gitCommitIdout), "\n", "")
    //buildMode := string(ctx.Config().Getenv("TARGET_BUILD_VARIANT"))
    //hostName := string(hostNameout)
    gitComitPd := strings.ReplaceAll(string(gitComitPdout), "\n", "")
    gitComitChandId := strings.ReplaceAll(string(gitComitChandIdout), "\n", "")

    asplayerVersion := fmt.Sprintf("V%s.%s.%s-g%s", majorVersion, subVersion, commitNums, gitCommitId)

    // replace version fields of BuildConfiguration
    replaceBuildConfigStringField(javaBuildConfigFile, "BRANCH_NAME", gitBranchName)
    replaceBuildConfigStringField(javaBuildConfigFile, "COMMIT_CHANGE_ID", gitComitChandId)
    replaceBuildConfigStringField(javaBuildConfigFile, "COMMIT_PD", gitComitPd)
    replaceBuildConfigStringField(javaBuildConfigFile, "LAST_CHANGED", gitLastChang)
    replaceBuildConfigStringField(javaBuildConfigFile, "BUILD_TIME", buildTime)
    replaceBuildConfigStringField(javaBuildConfigFile, "BUILD_NAME", buildName)
    replaceBuildConfigStringField(javaBuildConfigFile, "GIT_UN_COMMIT_FILE_NUM", gitUncommitFileNum)
    replaceBuildConfigStringField(javaBuildConfigFile, "VERSION_NAME", asplayerVersion)
    replaceBuildConfigBoolField(javaBuildConfigFile, "HAVE_VERSION_INFO", true)
}

func init() {
    android.RegisterModuleType("asplayerSharelibAndGitInfo_defaults", asplayerSharelibAndGitInfoFactory)
}

func asplayerSharelibAndGitInfoFactory() android.Module {
    module := java.DefaultsFactory()
    android.AddLoadHook(module, asplayerSharelibAndGitInfoDefaults)
    return module
}
