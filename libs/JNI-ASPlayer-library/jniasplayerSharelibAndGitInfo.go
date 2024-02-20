package jniasplayerSharelibAndGitInfo

import (
    "android/soong/android"
    "android/soong/cc"
    "os/exec"
    "fmt"
    "strings"
    "strconv"
    "regexp"
)

func jniasplayerSharelibAndGitInfoDefaults(ctx android.LoadHookContext) {
    sdkVersion := ctx.DeviceConfig().PlatformVndkVersion()
    sdkVersionInt,err := strconv.Atoi(sdkVersion)
    if err != nil {
        fmt.Printf("%v fail to convert", err)
    } else {
        fmt.Println("JNI-ASPlayer sdkVersion:", sdkVersionInt)
    }

    type props struct {
        Cflags []string
    }
    p := &props{}
    p.Cflags = setversion(ctx)
    ctx.AppendProperties(p)
}

func execCommand(cmd string, message string) ([]byte, error) {
    cmdOut, cmdErr := exec.Command("/bin/bash", "-c", cmd).CombinedOutput()
    if (cmdErr != nil) {
        fmt.Printf("ASPlayer %s failed %s\n", message, cmdErr)
    }
    return cmdOut, cmdErr
}

func setversion(ctx android.BaseContext) ([]string) {
    var cppflags []string
    var dir = string(ctx.Config().Getenv("PWD"))

    sdkVersion := ctx.DeviceConfig().PlatformVndkVersion()

    sourceDir := dir + "/vendor/amlogic/common/ASPlayer"
    versionFile := sourceDir + "/version/VERSION"
    // fmt.Println("JNI-ASPlayer versionFile: ", versionFile)

    networkUnreachableReg := regexp.MustCompile("(.*)Network is unreachable")

    majorVersionCmd := "sed -n 's/Major_V=\\(.*\\)/\\1/p' " + versionFile
    majorVersionout, _ := execCommand(majorVersionCmd, "get major version")
    majorVersion := strings.ReplaceAll(string(majorVersionout), "\n", "")
    majorVersion = networkUnreachableReg.ReplaceAllString(majorVersion, "")
    fmt.Printf("JNI-ASPlayer majorVersion: %s\n", majorVersion)

    subVersionCmd := "sed -n 's/Minor_V=\\(.*\\)/\\1/p' " + versionFile
    subVersionout, _ := execCommand(subVersionCmd, "get sub version")
    subVersion := strings.ReplaceAll(string(subVersionout), "\n", "")
    subVersion = networkUnreachableReg.ReplaceAllString(subVersion, "")
    fmt.Printf("JNI-ASPlayer subVersion: %s\n", subVersion)

    baseChangeIdCmd := "grep \"^BaseChangeId\" " + versionFile + " | awk -F [=] '{print $2}' | cut -c1-6"
    baseChangeIdout, _ := execCommand(baseChangeIdCmd, "get base change id")
    baseChangeId := strings.ReplaceAll(string(baseChangeIdout), "\n", "")

    buildTimeout, _ := execCommand("date", "get build time")
    buildTime := strings.ReplaceAll(string(buildTimeout), "\n", "")

    //hostNameout, hostNameerr := exec.Command("/bin/bash", "-c", "hostname").CombinedOutput()
    //if hostNameerr != nil {
    //    fmt.Printf("hostNameerr %s\n", hostNameerr)
    //}

    //gitVersion := string(gitVersionout)
    buildName := strings.ReplaceAll(string(ctx.Config().Getenv("LOGNAME")), "\n", "")
    //buildMode := string(ctx.Config().Getenv("TARGET_BUILD_VARIANT"))
    //hostName := string(hostNameout)

    commitNumsCmd := "cd " + sourceDir + " && git log | grep \"Change-Id: \" | grep -n " + baseChangeId + " | awk -F \":\" '{printf \"%d\", $1-1}'"
    commitNumsout, commitNumsErr := execCommand(commitNumsCmd, "get git commit count")
    commitNums := ""
    if (commitNumsErr == nil) {
        commitNums = strings.ReplaceAll(string(commitNumsout), "\n", "")
        commitNums = networkUnreachableReg.ReplaceAllString(commitNums, "")
    }

    gitCommitIdCmd := "cd "+ sourceDir + " && git rev-parse --short HEAD"
    gitCommitIdout, gitCommitIdErr := execCommand(gitCommitIdCmd, "get git commit id")
    gitCommitId := ""
    if (gitCommitIdErr == nil) {
        gitCommitId = strings.ReplaceAll(string(gitCommitIdout), "\n", "")
        gitCommitId = networkUnreachableReg.ReplaceAllString(gitCommitId, "")
        fmt.Printf("JNI-ASPlayer gitCommitId: %s\n", gitCommitId)
    }

    asplayerVersion := fmt.Sprintf("V%s.%s.%s-g%s", majorVersion, subVersion, commitNums, gitCommitId)

    hasVersionMacro := "-DHAVE_VERSION_INFO"
    fmt.Printf("JNI-ASPlayer %s\n", hasVersionMacro)
    cppflags = append(cppflags, hasVersionMacro)

    //ver2 := "-DGIT_VERSION=" + "\"" + gitVersion + "\""
    //fmt.Println(string(ver2))
    //cppflags = append(cppflags, ver2)

    buildTimeMacro := "-DBUILD_TIME=" + "\"" + buildTime + "\""
    fmt.Printf("JNI-ASPlayer %s\n", buildTimeMacro)
    cppflags = append(cppflags, buildTimeMacro)

    buildNameMacro := "-DBUILD_NAME=" + "\"" + buildName + "\""
    fmt.Printf("JNI-ASPlayer %s\n", buildNameMacro)
    cppflags = append(cppflags, buildNameMacro)

    platformSdkVersionMacro := "-DANDROID_PLATFORM_SDK_VERSION=" + sdkVersion
    fmt.Printf("JNI-ASPlayer %s\n", platformSdkVersionMacro)
    cppflags = append(cppflags, platformSdkVersionMacro)

    majorVersionMacro := "-DMAJORV=" + majorVersion
    fmt.Printf("JNI-ASPlayer %s\n", majorVersionMacro)
    cppflags = append(cppflags, majorVersionMacro)

    subVersionMacro := "-DMINORV=" + subVersion
    fmt.Printf("JNI-ASPlayer %s\n", subVersionMacro)
    cppflags = append(cppflags, subVersionMacro)

    versionNameMacro := "-DASPLAYER_VERSION=" + "\"" + string(asplayerVersion) + "\""
    fmt.Printf("JNI-ASPlayer %s\n", versionNameMacro)
    cppflags = append(cppflags, versionNameMacro)

    gitBranchNameCmd := "cd " + sourceDir + " && git branch -a | sed -n '/'*'/p'| awk '$1=$1'"
    gitBranchNameout, gitBranchNameErr := execCommand(gitBranchNameCmd, "get git branch name")
    if (gitBranchNameErr == nil) {
        gitBranchName := strings.ReplaceAll(string(gitBranchNameout), "\n", "")
        gitBranchName = networkUnreachableReg.ReplaceAllString(gitBranchName, "")

        branchNameMacro := "-DBRANCH_NAME=" + "\"" + gitBranchName + "\""
        fmt.Printf("JNI-ASPlayer %s\n", branchNameMacro)
        cppflags = append(cppflags, branchNameMacro)
    }

    gitLastChangeCmd := "cd " + sourceDir + " && git log | grep Date -m 1 | awk '$1=$1'"
    gitLastChangeout, gitLastChangeErr := execCommand(gitLastChangeCmd, "get last changed")
    if (gitLastChangeErr == nil) {
        gitLastChange := strings.ReplaceAll(string(gitLastChangeout), "\n", "")
        gitLastChange = networkUnreachableReg.ReplaceAllString(gitLastChange, "")

        lastChangeMacro := "-DLAST_CHANGED=" + "\"" + gitLastChange + "\""
        fmt.Printf("JNI-ASPlayer %s\n", lastChangeMacro)
        cppflags = append(cppflags, lastChangeMacro)
    }

    gitUncommitFileNumcmd := "cd " + sourceDir + " && git diff | grep +++ -c"
    gitUncommitFileNumout, gitUncommitFileNumErr := execCommand(gitUncommitFileNumcmd, "get git uncommit file num")
    if (gitUncommitFileNumErr == nil) {
        gitUncommitFileNum := strings.ReplaceAll(string(gitUncommitFileNumout), "\n", "")
        gitUncommitFileNum = networkUnreachableReg.ReplaceAllString(gitUncommitFileNum, "")

        uncommitFileNumMacro := "-DGIT_UNCOMMIT_FILE_NUM=" + "\"" + gitUncommitFileNum + "\""
        fmt.Printf("JNI-ASPlayer %s\n", uncommitFileNumMacro)
        cppflags = append(cppflags, uncommitFileNumMacro)
    }

    gitCommitPdCmd := "cd " + sourceDir + " && git log | grep PD# -m 1 | awk '$1=$1'"
    gitCommitPdout, gitCommitPdErr := execCommand(gitCommitPdCmd, "get pd id")
    if (gitCommitPdErr == nil) {
        gitCommitPd := strings.ReplaceAll(string(gitCommitPdout), "\n", "")
        gitCommitPd = networkUnreachableReg.ReplaceAllString(gitCommitPd, "")

        commitPdMacro := "-DCOMMIT_PD=" + "\"" + gitCommitPd + "\""
        fmt.Printf("JNI-ASPlayer %s\n", commitPdMacro)
        cppflags = append(cppflags, commitPdMacro)
    }

    gitCommitChangeIdCmd := "cd " + sourceDir + " && git log | grep Change-Id -m 1 | awk '$1=$1'"
    gitCommitChangeIdout, gitCommitChangeIdErr := execCommand(gitCommitChangeIdCmd, "get commit change id")
    if (gitCommitChangeIdErr == nil) {
        gitCommitChangeId := strings.ReplaceAll(string(gitCommitChangeIdout), "\n", "")
        gitCommitChangeId = networkUnreachableReg.ReplaceAllString(gitCommitChangeId, "")

        changeIdMacro := "-DCOMMIT_CHANGEID=" + "\"" + gitCommitChangeId + "\""
        fmt.Printf("JNI-ASPlayer %s\n", changeIdMacro)
        cppflags = append(cppflags, changeIdMacro)
    }

    return cppflags
}

func init() {
    android.RegisterModuleType("jniasplayerSharelibAndGitInfo_defaults", jniasplayerSharelibAndGitInfoFactory)
}

func jniasplayerSharelibAndGitInfoFactory() android.Module {
    module := cc.DefaultsFactory()
    android.AddLoadHook(module, jniasplayerSharelibAndGitInfoDefaults)

    return module
}
