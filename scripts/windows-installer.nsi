Unicode true
!include "MUI2.nsh"
!include "x64.nsh"
!include "WinVer.nsh"
!include "FileFunc.nsh"

Name "MinesweeperEX"
OutFile "${OUTPUT_FILE}"
InstallDir "$LOCALAPPDATA\Programs\MinesweeperEX"
InstallDirRegKey HKCU "Software\MinesweeperEX" "InstallDir"
RequestExecutionLevel user
SetCompressor zlib
ShowInstDetails show
ShowUninstDetails show
VIProductVersion "${APP_VERSION}.0"
VIAddVersionKey "ProductName" "MinesweeperEX"
VIAddVersionKey "FileDescription" "MinesweeperEX Setup"
VIAddVersionKey "FileVersion" "${APP_VERSION}.0"
VIAddVersionKey "ProductVersion" "${APP_VERSION}"
VIAddVersionKey "LegalCopyright" "MinesweeperEX contributors"

!define MUI_ABORTWARNING
!define MUI_FINISHPAGE_RUN "$INSTDIR\MinesweeperEX.exe"
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "SimpChinese"

Function .onInit
    SetRegView 64
    SetShellVarContext current
    ${IfNot} ${RunningX64}
        MessageBox MB_ICONSTOP "MinesweeperEX requires 64-bit Windows 10 or newer."
        Abort
    ${EndIf}
    ${IfNot} ${AtLeastWin10}
        MessageBox MB_ICONSTOP "MinesweeperEX requires Windows 10 or newer."
        Abort
    ${EndIf}
FunctionEnd

Function .onVerifyInstDir
    ${GetRoot} "$INSTDIR" $0
    ${If} $INSTDIR == "$0\"
    ${OrIf} $INSTDIR == "$WINDIR"
    ${OrIf} $INSTDIR == "$SYSDIR"
    ${OrIf} $INSTDIR == "$PROFILE"
    ${OrIf} $INSTDIR == "$DESKTOP"
    ${OrIf} $INSTDIR == "$LOCALAPPDATA"
    ${OrIf} $INSTDIR == "$PROGRAMFILES64"
        SetErrors
    ${EndIf}
FunctionEnd

Section "MinesweeperEX" Main
    SetOutPath "$INSTDIR"
    InitPluginsDir
    File /oname=$PLUGINSDIR\application.zip "${APP_ARCHIVE}"
    System::Call 'kernel32::SetEnvironmentVariable(t "MINESWEEPER_INSTALL_ARCHIVE", t "$PLUGINSDIR\application.zip")'
    System::Call 'kernel32::SetEnvironmentVariable(t "MINESWEEPER_INSTALL_DESTINATION", t "$INSTDIR")'
    DetailPrint "Installing game and bundled Java runtime..."
    nsExec::ExecToLog '"$WINDIR\Sysnative\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -NonInteractive -Command "Set-Variable -Name ProgressPreference -Value SilentlyContinue; Expand-Archive -LiteralPath $$env:MINESWEEPER_INSTALL_ARCHIVE -DestinationPath $$env:MINESWEEPER_INSTALL_DESTINATION -Force -ErrorAction Stop"'
    Pop $0
    System::Call 'kernel32::SetEnvironmentVariable(t "MINESWEEPER_INSTALL_ARCHIVE", p 0)'
    System::Call 'kernel32::SetEnvironmentVariable(t "MINESWEEPER_INSTALL_DESTINATION", p 0)'
    ${If} $0 != "0"
        MessageBox MB_ICONSTOP "Installation could not finish. Please choose a writable folder." /SD IDOK
        SetErrorLevel 1
        Abort
    ${EndIf}
    WriteUninstaller "$INSTDIR\Uninstall.exe"
    CreateDirectory "$SMPROGRAMS\MinesweeperEX"
    CreateShortcut "$SMPROGRAMS\MinesweeperEX\MinesweeperEX.lnk" "$INSTDIR\MinesweeperEX.exe"
    CreateShortcut "$DESKTOP\MinesweeperEX.lnk" "$INSTDIR\MinesweeperEX.exe"
    WriteRegStr HKCU "Software\MinesweeperEX" "InstallDir" "$INSTDIR"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MinesweeperEX" "DisplayName" "MinesweeperEX"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MinesweeperEX" "DisplayVersion" "${APP_VERSION}"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MinesweeperEX" "Publisher" "MinesweeperEX"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MinesweeperEX" "InstallLocation" "$INSTDIR"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MinesweeperEX" "DisplayIcon" "$INSTDIR\MinesweeperEX.exe"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MinesweeperEX" "UninstallString" '"$INSTDIR\Uninstall.exe"'
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MinesweeperEX" "QuietUninstallString" '"$INSTDIR\Uninstall.exe" /S'
    WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MinesweeperEX" "NoModify" 1
    WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MinesweeperEX" "NoRepair" 1
    WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MinesweeperEX" "EstimatedSize" ${SIZE_KB}
SectionEnd

Function un.onInit
    SetRegView 64
    SetShellVarContext current
FunctionEnd

Section "Uninstall"
    !include "${UNINSTALL_MANIFEST}"
    Delete "$INSTDIR\Uninstall.exe"
    RMDir "$INSTDIR"
    ReadRegStr $0 HKCU "Software\MinesweeperEX" "InstallDir"
    ${If} $0 == $INSTDIR
        Delete "$DESKTOP\MinesweeperEX.lnk"
        Delete "$SMPROGRAMS\MinesweeperEX\MinesweeperEX.lnk"
        RMDir "$SMPROGRAMS\MinesweeperEX"
        DeleteRegKey HKCU "Software\MinesweeperEX"
        DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MinesweeperEX"
    ${EndIf}
SectionEnd
