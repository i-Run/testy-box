* :arrow_right: **Analyse technique à faire**
* :arrow_right: **Analyse technique à valider**

## Analyse technique

> Descriptif technique du problème ou de la fonctionnalité et des choix pour l’implémenter.

### Découpage

> Si nécessaire indiquer le liste des sous-taches nécessaire à réalisation de la fonctionnalité

- [ ] Tâche technique 1
- [ ] Tâche technique 2

## Développement

> Indiquer toute information constatée pendant le développement, pouvant impacter la compréhension du problème ou sa résolution

### Pré Merge Request checklist

Avant d’envoyer une merge request :

* [ ] J'ai relu la spec fonctionnelle et toutes les features sont là
* [ ] J'ai testé mon code dans unchained et dans le legacy si nécessaire
* [ ] La couverture de test couvre plus de 80% des classes que j'ai modifié
* [ ] J'ai relu mon code
* [ ] Mon code ne contient pas de Warning
* [ ] J'ai lancé `mvn clean compile checkstyle:check` pour m'assurer de ma syntaxe
* [ ] J'ai lancé `mvn clean package javadoc:javadoc` pour m'assurer que ma javadoc n’a pas d'erreur
* [ ] Mon build maven n’affiche pas de WARNING en dehors des tests d’intégration ignored

