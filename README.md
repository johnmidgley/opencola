<img src="img/pull-tab.svg" width="150" alt="OpenCola"/>


# OpenCola


This repository contains the code for the OpenCola application and toolkit. To understand the overall vision of OpenCola, which puts this code in a larger context please visit our [homepage](https://opencola.io).

## Application
The application is a collaboration tool that looks a lot like a social media application, but puts you in control of your personal data as well as the flow of information around you. This means that, unless you decide otherwise, there is no 3rd party ingerference (ads, algorithms, trolls, scammers, etc.). You also control where your data lives, which is by default on your local device, shared with only peers of your choice. The image below shows what the application looks like, which, on the surface, is similar to other social media applications:

<img src="img/feed.png" alt="Feed">

## Toolkit
The toolkit provides is a flexible [data model](./opencola-server/core/model/README.md), a set of simple interfaces ([`EntityStore`](./opencola-server/core/storage/README.md#entitystore), [`ContentAddressedFileStore`](./opencola-server/core/storage/README.md#filestore), [`NetworkProvider`](./opencola-server/core/network/README.md#network-providers) and [`SearchIndex`](./opencola-server/core/search/README.md#search)), supporting libraries, as well as a [relay server](./opencola-server/relay/README.md) that allows peer nodes to communicate in a trustworthy way. The toolkit is very general, and could be used to build many applications across arbitrary domains. Here are some other applications we're thinking about:


- **Healthcare**: Healthcare consumers have to rely on proprietary
        software to manage transport of healthcare records. This software is
        controlled by companies who have an incentive to lock in lucrative
        contracts in order to maximize profit, which raises healthcare
        costs. Imagine instead having direct control of your healthcare
        records that you can connect with your various healthcare providers.
        Get test results directly from labs that, under your control,
        automatically synchronize with your doctors. When you switch
        doctors, just disconnect your old doctor and connect your new
        doctor. You would get greater flexibility, offered at lower cost,
        because it uses an open, general platform.
- **Education**: With some simple modifications (e.g. limiting connections to
        classmates), the OpenCola collaboration tool could be useful in a classroom
        setting.
- **Journalism**: News outlets have been hurt by social media platforms siphoning
        away audience and ad revenue. OpenCola could allow these organizations to
        interact directly with their audience, restoring hard earned value to the
        organizations.
- **User Centric Recommendations**: Since OpenCola allows you to collect activity from any site,
        there’s a lot more information that could be used to help you find
        things of interest. Imagine being able to choose a YouTube recommendation algorithm,
        built outside of YouTube, optimized for you that could get you out of
        filter bubbles. Or an Amazon product recommendation system that can’t be
        manipulated by Amazon to maximize profit, that can make use of your
        network’s product ratings instead of those from unknown people on Amazon
        that are gamed by bots.
- **Creative Markets (Music, Books, YouTubers, etc.)**: Creators spend great time
        and effort to build loyal audiences but are at the mercy of corporate
        intermediaries (Amazon, Record Labels, YouTube, etc.) for their success.
        OpenCola could support direct interactions (including sales) between
        creators and their communities, so creators have more control over their
        livelihood.
- **Open Banking**: Your financial information is generally locked in numerous
        proprietary silos, making it hard to get a single picture of your financial
        state. Some products allow you to merge the data from all your financial
        institutions, but they require that you let them see all of this private
        information. OpenCola could be used to model financial transactions and
        allow for applications that let you see your financial data the way you want
        to see it, without having to expose this data to anyone else.

# Navigating the Code

If you would like to understand the code bottom up, start in the [`model`](opencola-server/core/model/README.md). If you would like to start top down, start in [`Application.kt`](opencola-server/server/src/main/kotlin/opencola/server/Application.kt)

The important parts of the code are: 

|Directory|Description|
|------|------|
|[`opencola-server`](opencola-server/README.md)| Backend code (oc, relay) (Kotlin) |
|[`web-ui`](web-ui/README.md)| The frontend for the application (Clojurescript)|
|[`extension`](extension/chrome/README.md)| The chrome browser extension (HTML, JS, CSS)|
|[`install`](install/README.md)| Scripts building and generating installers for the collaboration tool (Bash) |





