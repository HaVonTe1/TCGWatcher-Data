# TCGWatcher-Data

## Overview
This repository contains a Spring Boot application that processes data from the Pokémon TCG Dex and stores it in an SQLite database. The code is written in Java 21 and uses Maven for build, test and dependency management.

## Essential Commands
| Task | Command |
|------|---------|
| Build project | `./mvnw clean install` |
| Run application locally (dev mode) | `./mvnw spring-boot:run` |
| Run tests | `./mvnw test` |
| Generate JAR for deployment | `./mvnw package` |
| View effective POM after all plugin imports | `./mvnw help:effective-pom` |

> **Note** – The project ships a Maven wrapper (`mvnw`, `mvnw.cmd`) and the `.mvn/wrapper/` directory. Use these scripts to avoid installing Maven locally.

## Project Structure
```
src/main/java          # Java source code
  de/dktutzer/tcgwatcher/data/
    TcgWatcherDataApplication.java   # Spring Boot entry point
    TcgDataCreatorCommand.java       # Spring Shell command for data creation
    config/                           # Spring configuration classes
    data/                             # DTOs, models and entities
    service/                          # Business logic & persistence
src/main/resources     # application.yml, SQL scripts, static resources
src/test/java          # Unit tests
output/                # Generated JSON files (cardsFromTCGDex.json, setsFromTcgDexWithCode.json)
```
*The `output` directory is populated by the `TcgDataCreatorCommand`. It is not part of the source tree but may be checked in for CI artefacts.*

## Naming & Style Conventions
- Packages: all lower‑case, hierarchical under `de.dktutzer.tcgwatcher.data`.
- Classes: PascalCase, one per file. Entity classes end with `Entity`, DTOs with `Dto`, models with `Model`.
- Lombok is optional (`@Getter`, `@Setter`, etc.). Ensure the Lombok plugin is available in your IDE.
- The project uses OpenRewrite recipes to enforce Google Java Format and static analysis checks.

## Testing Approach
- Tests are located under `src/test/java`. They use JUnit 5 and Spring Boot's test slice annotations.
- Run `./mvnw test` for a full suite. Individual tests can be executed with Maven Surefire or via your IDE.
- No integration tests that hit external APIs are present; data is loaded from the bundled JSON files in `output/`.

## Build & Linting
- The build pipeline is handled by Maven. The pom defines plugins for:
  - Spring Boot packaging (`spring-boot-maven-plugin`).
  - OpenRewrite static analysis and code formatting.
  - Versions plugin for dependency upgrades.
- Running `./mvnw verify` will execute all checks, including the rewrite recipes.

## Gotchas & Non‑Obvious Patterns
1. **SQLite JDBC** – The application uses the SQLite driver (`org.xerial:sqlite-jdbc`). Ensure the native binaries are available on your platform; they are bundled with the Maven dependency.
2. **Spring Shell** – The `TcgDataCreatorCommand` is a Spring Shell command. It can be invoked from the running app by typing `create-data` in the console.
3. **Lombok Optional** – Lombok annotations are marked `optional=true`. If you remove Lombok, manually add getters/setters or use Java record types.
4. **OpenRewrite Recipes** – Running `./mvnw verify` will automatically format code and enforce best practices; manual formatting is discouraged to avoid merge conflicts.
5. **Configuration Files** – Application properties are located in `src/main/resources/application.yml`. The default profile loads SQLite settings from `application.yml`.

## CI / GitHub Actions
No `.github/workflows` directory exists yet, but a `dependabot.yml` is present to keep dependencies up‑to‑date.

---
**Created by Crush**