package jniasplayerSharelibAndGitInfo

import (
    "android/soong/android"
    "android/soong/cc"
    "os/exec"
    "fmt"
    "strings"
    "strconv"
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
    if cmdErr != nil {
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

    majorVersionCmd := "sed -n 's/Major_V=\\(.*\\)/\\1/p' " + versionFile
    majorVersionout, _ := execCommand(majorVersionCmd, "get major version")
    majorVersion := strings.ReplaceAll(string(majorVersionout), "\n", "")
    fmt.Printf("JNI-ASPlayer majorVersion: %s\n", majorVersion)

    subVersionCmd := "sed -n 's/Minor_V=\\(.*\\)/\\1/p' " + versionFile
    subVersionout, _ := execCommand(subVersionCmd, "get sub version")
    subVersion := strings.ReplaceAll(string(subVersionout), "\n", "")
    fmt.Printf("JNI-ASPlayer subVersion: %s\n", subVersion)

    baseChangeIdCmd := "grep \"^BaseChangeId\" " + versionFile + " | awk -F [=] '{print $2}' | cut -c1-6"
    baseChangeIdout, _ := execCommand(baseChangeIdCmd, "get base change id")

    commitNumsCmd := "cd " + sourceDir + " && git log | grep \"Change-Id: \" | grep -n " + strings.Replace(string(baseChangeIdout),"\n","",-1) + " | awk -F \":\" '{printf \"%d\", $1-1}'"
    commitNumsout, _ := execCommand(commitNumsCmd, "get git commit count")

    gitCommitIdCmd := "cd "+ sourceDir + " && git rev-parse --short HEAD"
    gitCommitIdout, _ := execCommand(gitCommitIdCmd, "get git commit id")

    gitUncommitFileNumcmd := "cd " + sourceDir + " && git diff | grep +++ -c"
    gitUncommitFileNumout, _ := execCommand(gitUncommitFileNumcmd, "get git uncommit file num")

    gitLastChangCmd := "cd " + sourceDir + " && git log | grep Date -m 1 | awk '$1=$1'"
    gitLastChangout, _ := execCommand(gitLastChangCmd, "get last changed")

    buildTimeout, _ := execCommand("date", "get build time")

    gitBranchNameCmd := "cd " + sourceDir + " && git branch -a | sed -n '/'*'/p'| awk '$1=$1'"
    gitBranchNameout, _ := execCommand(gitBranchNameCmd, "get git branch name")

    //hostNameout, hostNameerr := exec.Command("/bin/bash", "-c", "hostname").CombinedOutput()
    //if hostNameerr != nil {
    //    fmt.Printf("hostNameerr %s\n", hostNameerr)
    //}

    gitComitPdCmd := "cd " + sourceDir + " && git log | grep PD# -m 1 | awk '$1=$1'"
    gitComitPdout, _ := execCommand(gitComitPdCmd, "get pd id")

    gitComitChandIdCmd := "cd " + sourceDir + " && git log | grep Change-Id -m 1 | awk '$1=$1'"
    gitComitChandIdout, _ := execCommand(gitComitChandIdCmd, "get commit change id")

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

    ver1 := "-DHAVE_VERSION_INFO"
    fmt.Printf("JNI-ASPlayer %s\n", ver1)
    cppflags = append(cppflags, ver1)

    //ver2 := "-DGIT_VERSION=" + "\"" + gitVersion + "\""
    //fmt.Println(string(ver2))
    //cppflags = append(cppflags, ver2)

    ver3 := "-DBRANCH_NAME=" + "\"" + gitBranchName + "\""
    fmt.Printf("JNI-ASPlayer %s\n", ver3)
    cppflags = append(cppflags, ver3)

    ver4 := "-DLAST_CHANGED=" + "\"" + gitLastChang + "\""
    fmt.Printf("JNI-ASPlayer %s\n", ver4)
    cppflags = append(cppflags, ver4)

    ver5 := "-DBUILD_TIME=" + "\"" + buildTime + "\""
    fmt.Printf("JNI-ASPlayer %s\n", ver5)
    cppflags = append(cppflags, ver5)

    ver6 := "-DBUILD_NAME=" + "\"" + buildName + "\""
    fmt.Printf("JNI-ASPlayer %s\n", ver6)
    cppflags = append(cppflags, ver6)

    ver7 := "-DGIT_UNCOMMIT_FILE_NUM=" + "\"" + gitUncommitFileNum + "\""
    fmt.Printf("JNI-ASPlayer %s\n", ver7)
    cppflags = append(cppflags, ver7)

    ver8 := "-DCOMMIT_PD=" + "\"" + gitComitPd + "\""
    fmt.Printf("JNI-ASPlayer %s\n", ver8)
    cppflags = append(cppflags, ver8)

    ver9 := "-DCOMMIT_CHANGEID=" + "\"" + gitComitChandId + "\""
    fmt.Printf("JNI-ASPlayer %s\n", ver9)
    cppflags = append(cppflags, ver9)

    ver10 := "-DANDROID_PLATFORM_SDK_VERSION=" + sdkVersion
    fmt.Printf("JNI-ASPlayer %s\n", ver10)
    cppflags = append(cppflags, ver10)

    ver11 := "-DMAJORV=" + string(majorVersion)
    fmt.Printf("JNI-ASPlayer %s\n", ver11)
    cppflags = append(cppflags, ver11)

    ver12 := "-DMINORV=" +  string(subVersion)
    fmt.Printf("JNI-ASPlayer %s\n", ver12)
    cppflags = append(cppflags, ver12)

    ver13 := "-DASPLAYER_VERSION=" + "\"" + string(asplayerVersion) + "\""
    fmt.Printf("JNI-ASPlayer %s\n", ver13)
    cppflags = append(cppflags, ver13)

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
