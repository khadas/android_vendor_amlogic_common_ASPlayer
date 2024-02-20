package asplayerSharelibAndGitInfo

import (
    "android/soong/android"
    "android/soong/java"
    "os/exec"
    "fmt"
    "strings"
    "strconv"
    "regexp"
)

func asplayerSharelibAndGitInfoDefaults(ctx android.LoadHookContext) {
    sdkVersion := ctx.DeviceConfig().PlatformVndkVersion()
    sdkVersionInt, err := strconv.Atoi(sdkVersion)
    if (err != nil) {
        fmt.Printf("%v fail to convert", err)
    } else {
        fmt.Println("ASPlayer sdkVersion:", sdkVersionInt)
    }

    setversion(ctx)
}

func execCommand(cmd string, message string) ([]byte, error) {
    cmdOut, cmdErr := exec.Command("/bin/bash", "-c", cmd).CombinedOutput()
    if (cmdErr != nil) {
        fmt.Printf("ASPlayer %s failed %s\n", message, cmdErr)
    }
    return cmdOut, cmdErr
}

func replaceBuildConfigStringField(filePath string, fieldName string, newVal string) ([]byte, error) {
    // public static final String XX = "xx";
    cmd := fmt.Sprintf("sed -i -r 's/%s(.*)=(.*);/%s = \"%s\";/g' %s", fieldName, fieldName, newVal, filePath)
    cmdOut, cmdErr := exec.Command("/bin/bash", "-c", cmd).CombinedOutput()
    if (cmdErr != nil) {
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

    networkUnreachableReg := regexp.MustCompile("(.*)Network is unreachable")

    majorVersionCmd := "sed -n 's/Major_V=\\(.*\\)/\\1/p' " + versionFile
    majorVersionout, _ := execCommand(majorVersionCmd, "get major version")
    majorVersion := strings.ReplaceAll(string(majorVersionout), "\n", "")
    majorVersion = networkUnreachableReg.ReplaceAllString(majorVersion, "")
    fmt.Printf("ASPlayer majorVersion: %s\n", majorVersion)

    subVersionCmd := "sed -n 's/Minor_V=\\(.*\\)/\\1/p' " + versionFile
    subVersionout, _ := execCommand(subVersionCmd, "get sub version")
    subVersion := strings.ReplaceAll(string(subVersionout), "\n", "")
    subVersion = networkUnreachableReg.ReplaceAllString(subVersion, "")
    fmt.Printf("ASPlayer subVersion: %s\n", subVersion)

    baseChangeIdCmd := "grep \"^BaseChangeId\" " + versionFile + " | awk -F [=] '{print $2}' | cut -c1-6"
    baseChangeIdout, _ := execCommand(baseChangeIdCmd, "get base change id")
    baseChangeId := strings.ReplaceAll(string(baseChangeIdout), "\n", "")

    buildTimeout, _ := execCommand("date", "get build time")
    //gitVersion := string(gitVersionout)
    buildTime := strings.ReplaceAll(string(buildTimeout), "\n", "")
    buildName := strings.ReplaceAll(string(ctx.Config().Getenv("LOGNAME")), "\n", "")
    //buildMode := string(ctx.Config().Getenv("TARGET_BUILD_VARIANT"))
    //hostName := string(hostNameout)

    commitNumsCmd := "cd " + sourceDir + " && git log | grep \"Change-Id: \" | grep -n " + baseChangeId + " | awk -F \":\" '{printf \"%d\", $1-1}'"
    commitNumsOut, commitNumsErr := execCommand(commitNumsCmd, "get commit count")
    commitNums := ""
    if (commitNumsErr == nil) {
        commitNums = strings.ReplaceAll(string(commitNumsOut), "\n", "")
        commitNums = networkUnreachableReg.ReplaceAllString(commitNums, "")
    }

    gitCommitIdCmd := "cd "+ sourceDir + " && git rev-parse --short HEAD"
    gitCommitIdout, gitCommitIdErr := execCommand(gitCommitIdCmd, "get commit id")
    gitCommitId := ""
    if (gitCommitIdErr == nil) {
        gitCommitId = strings.ReplaceAll(string(gitCommitIdout), "\n", "")
        gitCommitId = networkUnreachableReg.ReplaceAllString(gitCommitId, "")
    }

    asplayerVersion := fmt.Sprintf("V%s.%s.%s-g%s", majorVersion, subVersion, commitNums, gitCommitId)

    gitBranchNameCmd := "cd " + sourceDir + " && git branch -a | sed -n '/'*'/p'| awk '$1=$1'"
    gitBranchNameOut, gitBranchNameErr := execCommand(gitBranchNameCmd, "get git branch name")
    gitBranchName := ""
    if (gitBranchNameErr == nil) {
        gitBranchName = strings.ReplaceAll(string(gitBranchNameOut), "\n", "")
        gitBranchName = networkUnreachableReg.ReplaceAllString(gitBranchName, "")
    }

    gitCommitChangeIdCmd := "cd " + sourceDir + " && git log | grep Change-Id -m 1 | awk '$1=$1'"
    gitCommitChangeIdout, gitCommitChangeIdErr := execCommand(gitCommitChangeIdCmd, "get commit changed id")
    gitCommitChangeId := ""
    if (gitCommitChangeIdErr == nil) {
        gitCommitChangeId = strings.ReplaceAll(string(gitCommitChangeIdout), "\n", "")
        gitCommitChangeId = networkUnreachableReg.ReplaceAllString(gitCommitChangeId, "")
    }

    gitCommitPdCmd := "cd " + sourceDir + " && git log | grep PD# -m 1 | awk '$1=$1'"
    gitCommitPdout, gitCommitPdErr := execCommand(gitCommitPdCmd, "get commit pd")
    gitCommitPd := ""
    if (gitCommitPdErr == nil) {
        gitCommitPd = strings.ReplaceAll(string(gitCommitPdout), "\n", "")
        gitCommitPd = networkUnreachableReg.ReplaceAllString(gitCommitPd, "")
    }

    gitLastChangeCmd := "cd " + sourceDir + " && git log | grep Date -m 1 | awk '$1=$1'"
    gitLastChangeOut, gitLastChangeErr := execCommand(gitLastChangeCmd, "get last changed")
    gitLastChange := ""
    if (gitLastChangeErr == nil) {
        gitLastChange = strings.ReplaceAll(string(gitLastChangeOut), "\n", "")
        gitLastChange = networkUnreachableReg.ReplaceAllString(gitLastChange, "")
    }

    gitUncommitFileNumcmd := "cd " + sourceDir + " && git diff | grep +++ -c"
    gitUncommitFileNumOut, gitUncommitFileNumErr := execCommand(gitUncommitFileNumcmd, "get uncommit file number")
    gitUncommitFileNum := ""
    if (gitUncommitFileNumErr == nil) {
        gitUncommitFileNum = strings.ReplaceAll(string(gitUncommitFileNumOut), "\n", "")
        gitUncommitFileNum = networkUnreachableReg.ReplaceAllString(gitUncommitFileNum, "")
    }

    // replace version fields of BuildConfiguration
    if (gitBranchName != "") {
        replaceBuildConfigStringField(javaBuildConfigFile, "BRANCH_NAME", gitBranchName)
    }
    if (gitCommitChangeId != "") {
        replaceBuildConfigStringField(javaBuildConfigFile, "COMMIT_CHANGE_ID", gitCommitChangeId)
    }
    if (gitCommitPd != "") {
        replaceBuildConfigStringField(javaBuildConfigFile, "COMMIT_PD", gitCommitPd)
    }
    if (gitLastChange != "") {
        replaceBuildConfigStringField(javaBuildConfigFile, "LAST_CHANGED", gitLastChange)
    }
    replaceBuildConfigStringField(javaBuildConfigFile, "BUILD_TIME", buildTime)
    replaceBuildConfigStringField(javaBuildConfigFile, "BUILD_NAME", buildName)
    replaceBuildConfigStringField(javaBuildConfigFile, "VERSION_NAME", asplayerVersion)
    if (gitUncommitFileNum != "") {
        replaceBuildConfigStringField(javaBuildConfigFile, "GIT_UN_COMMIT_FILE_NUM", gitUncommitFileNum)
    }
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
