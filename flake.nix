{
  description = "Fullstack Clojure Starter";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    clj-nix.url = "github:jlesquembre/clj-nix";
  };

  outputs = { self, nixpkgs, clj-nix, ... }:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};
      buildDeps = [
        pkgs.jdk25
        pkgs.clojure
        pkgs.nodejs_20
        pkgs.chromium
        pkgs.python3Minimal
        pkgs.entr
      ];
    in {
      nixosModules = {
        ted = { ted, config, lib, pkgs, ... }:

          let
            pkg = ted.packages.${system}.default;
            cfg = config.services.ted;
          in {

            # --------------------------------------------------
            # Interface.
            options = {
              services.ted = {
                enable = lib.mkEnableOption (lib.mdDoc "Fullstack Clojure Starter");

                port = lib.mkOption {
                  type = lib.types.port;
                  description = lib.mdDoc ''
                    The port to listen on.
                  '';
                };
              };
            };

            # --------------------------------------------------
            # Implementation

            config = lib.mkIf cfg.enable  {

              systemd.services.ted = {
                enable = true;

                wantedBy = [
                  "multi-user.target"  # start on server boot.
                ];

                after = [];

                environment = {
                  PORT = builtins.toString cfg.port;
                };

                serviceConfig = {
                  User = "root";
                  Group = "root";
                  WorkingDirectory = pkg;
                  ExecStart = "${pkg}/bin/ted";
                };
              };

            };

          };
      };
      packages.${system} = rec {
        frontend-deps = pkgs.stdenvNoCC.mkDerivation {
          name = "ted-frontend-deps";
          src = ./.;

          nativeBuildInputs = [ pkgs.nodejs_20 pkgs.cacert ];

          buildPhase = ''
            mkdir -p $out
            cp package.json $out/

            # Set SSL certificates for npm
            export SSL_CERT_FILE=${pkgs.cacert}/etc/ssl/certs/ca-bundle.crt
            export NODE_EXTRA_CA_CERTS=${pkgs.cacert}/etc/ssl/certs/ca-bundle.crt

            # Install npm dependencies with fixed output
            HOME=$TMPDIR npm install --legacy-peer-deps

            # Copy node_modules to output
            cp -r node_modules $out/
          '';

          installPhase = ''
            # Output is already set in buildPhase
          '';

          # Allow network access for this specific derivation
          outputHashMode = "recursive";
          outputHashAlgo = "sha256";
          outputHash = "sha256-bNTpjnn5CjFj10Fyfbt2a2RP2Qj3o2Z8zhCpWR99WuQ=";
        };

        default = clj-nix.lib.mkCljApp {
          pkgs = nixpkgs.legacyPackages.${system};
          modules = [
            {
              projectSrc = ./.;
              name = "ted";
              main-ns = "app.backend.main";
              jdk = pkgs.jdk25_headless;
              buildCommand = ''
                # Create resources directory
                mkdir -p resources/public/js

                # Use pre-built npm dependencies
                ln -sf ${frontend-deps}/node_modules ./node_modules

                # Build frontend with shadow-cljs
                clojure -M:frontend compile app

                # Build backend uberjar
                clj-builder uber "ted/ted" "DEV" "app.backend.main" \
                  'null' \
                  'null' \
                  'null'
              '';
            }
          ];
        };
      };

      devShells.${system}.default = pkgs.mkShell {
        packages = buildDeps ++ [
          pkgs.gnumake
        ];
        shellHook = ''
          echo "Developing ted"
        '';
      };
    };
}
