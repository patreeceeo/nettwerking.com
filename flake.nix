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
        pkgs.curl
      ];
    in {
      nixosModules = {
        fullstack_clojure = { fullstack_clojure, config, lib, pkgs, ... }:

          let
            pkg = fullstack_clojure.packages.${system}.default;
            cfg = config.services.fullstack_clojure;
          in {

            # --------------------------------------------------
            # Interface.
            options = {
              services.fullstack_clojure = {
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

              systemd.services.fullstack_clojure = {
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
                  ExecStart = "${pkg}/bin/fullstack_clojure";
                };
              };

            };

          };
      };
      packages.${system} = rec {
        default = clj-nix.lib.mkCljApp {
          pkgs = nixpkgs.legacyPackages.${system};
          modules = [ # Option list: https://jlesquembre.github.io/clj-nix/options/
            {
              projectSrc = ./.;
              name = "fullstack_clojure";
              main-ns = "app.backend.main";
              jdk = pkgs.jdk25_headless;
              buildCommand = ''
                mkdir -p resources/public/js

                clojure -M:frontend compile app

                clj-builder uber "fullstack_clojure/fullstack_clojure" "DEV" "app.backend.main" \
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
          echo "Developing fullstack_clojure"
        '';
      };
    };
}
