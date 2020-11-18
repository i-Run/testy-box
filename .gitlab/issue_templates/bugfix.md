* :arrow_right: **Analyse technique à faire**
* :arrow_right: **Analyse technique à valider**

## Description du problème

> Décrire ici quel est le comportement attendu et quel est le comportement observé

### Pour reproduire

* Etape de reproduction 1
* Etape de reproduction 2
* ...

## Analyse technique

> Descriptif technique du problème ou de la fonctionnalité et des choix pour l’implémenter.

## Développement

> Indiquer toute information constatée pendant le développement, pouvant impacter la compréhension du problème ou sa résolution

### Pré Merge Request checklist

Avant d’envoyer une merge request :

* [ ] J’ai ajouté un test unitaire qui reproduit le problème
* [ ] J’ai testé mon code dans unchained et dans le legacy si nécessaire
* [ ] J’ai relus mon code
* [ ] Mon code ne contient pas de Warning
* [ ] J’ai lancé `mvn clean compile checkstyle:check` pour m’assurer de ma syntaxe
* [ ] J'ai lancé `mvn clean package javadoc:javadoc` pour m'assurer que ma javadoc n’a pas d’erreur
* [ ] Mon build maven n’affiche pas de WARNING en dehors des tests d’intégration ignored

