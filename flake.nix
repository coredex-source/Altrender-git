{
  # Optional dev shell for NixOS (development.md 0.2): `nix develop`, then ./gradlew as usual.
  # UNTESTED as of 2026-11-12 (written on a non-Nix box).
  description = "Altrender dev shell: JDK 21 + 25, apitrace, Mesa tools, game runtime libs";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" ];
      forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f nixpkgs.legacyPackages.${system});
    in
    {
      devShells = forAllSystems (pkgs:
        let
          # Libraries LWJGL 2/3, SDL3 and OpenAL Soft dlopen() at runtime; NixOS has no global /usr/lib to find them in.
          runtimeLibs = with pkgs; [
            libGL
            libglvnd
            xorg.libX11
            xorg.libXcursor
            xorg.libXrandr
            xorg.libXxf86vm
            xorg.libXext
            xorg.libXi
            xorg.libXfixes
            xorg.libXScrnSaver
            libxkbcommon
            libdecor
            wayland
            libpulseaudio
            pipewire
            alsa-lib
            udev
          ];
        in
        {
          default = pkgs.mkShell {
            packages = with pkgs; [
              jdk21
              jdk25
              apitrace
              mesa-demos # glxinfo / eglinfo
              xorg.xrandr # LWJGL2 runs `xrandr` for display modes
            ];
            # Gradle itself runs on 25 (Loom 1.19); the toolchains (21 for builds and runs, 25 for -Paltrender.runJava=25)
            # come from these variables, never from downloaded JDKs (they don't run on NixOS).
            JAVA_HOME = pkgs.jdk25.home;
            JDK21 = pkgs.jdk21.home;
            JDK25 = pkgs.jdk25.home;
            GRADLE_OPTS = "-Dorg.gradle.java.installations.fromEnv=JDK21,JDK25 -Dorg.gradle.java.installations.auto-download=false";
            LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath runtimeLibs;
            shellHook = ''
              echo "[Altrender] dev shell: $(java -version 2>&1 | head -1)"
              echo "  apitrace: ./gradlew :altrender:mc:b1.7.3:runClient -Paltrender.apitrace=${pkgs.apitrace}/lib/apitrace/wrappers/glxtrace.so"
            '';
          };
        });
    };
}
