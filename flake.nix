{
  description = "Fullstack Clojure Starter";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    clj-nix.url = "github:jlesquembre/clj-nix";
  };

  outputs = { nixpkgs, clj-nix, ... }:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};
      buildDeps = [
        pkgs.jdk25
        pkgs.clojure
        pkgs.gnumake
      ];
    in {
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
        buildInputs = buildDeps ++ [];
        shellHook = ''
          echo "Developing fullstack_clojure"
        '';
      };
    };
}
