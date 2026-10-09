// SPDX-License-Identifier: MIT
import SwiftUI

struct IntroScreen: View {
    var onContinue: () -> Void
    var onOpenLink: (String) -> Void
    @Environment(\.scoutColors) private var colors

    var body: some View {
        let legal = HelpContent.loadLegalSections().first
        ScrollView {
            VStack(spacing: ScoutSpacing.md) {
                Spacer().frame(height: ScoutSpacing.lg)
                ScoutLogo(markSize: 88, vertical: true)
                Text(L10n.brandTagline)
                    .font(ScoutType.body)
                    .foregroundStyle(colors.textSecondary)
                    .multilineTextAlignment(.center)
                VStack(alignment: .leading, spacing: ScoutSpacing.lg) {
                    IntroPoint(L10n.introPointTag)
                    IntroPoint(L10n.introPointPhoto)
                    IntroPoint(L10n.introPointRadar)
                    IntroPoint(L10n.introPointPrivate)
                }
                .padding(.top, ScoutSpacing.xl)
                Text(L10n.introPermissionsTitle)
                    .font(ScoutType.display)
                    .foregroundStyle(colors.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                VStack(alignment: .leading, spacing: ScoutSpacing.md) {
                    IntroPoint(L10n.introPermissionLocation)
                    IntroPoint(L10n.introPermissionBluetooth)
                    IntroPoint(L10n.introPermissionCamera)
                }
                Text(L10n.introPermissionsWhen)
                    .font(ScoutType.body)
                    .foregroundStyle(colors.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, ScoutSpacing.md)
                legalBlock(legal, onOpenLink: onOpenLink)
                Text(L10n.introPowering.uppercased())
                    .font(ScoutType.overline)
                    .foregroundStyle(colors.textSecondary)
                    .padding(.top, ScoutSpacing.xxl)
                InstanceWordmark()
                Text(L10n.introLegalAck)
                    .font(ScoutType.body)
                    .foregroundStyle(colors.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.top, ScoutSpacing.xl)
                ScoutButton(label: L10n.introContinue, primary: true, action: onContinue)
                    .padding(.top, ScoutSpacing.md)
                Spacer().frame(height: ScoutSpacing.lg)
            }
            .padding(.horizontal, ScoutSpacing.xl)
            .padding(.vertical, ScoutSpacing.xxl)
        }
        .background(colors.screen)
    }

    @ViewBuilder
    private func legalBlock(_ section: HelpSection?, onOpenLink: @escaping (String) -> Void) -> some View {
        let title = section?.heading ?? L10n.introLegalTitle
        let body = section?.body ?? []
        let links = section?.links.isEmpty == false ? section!.links : [
            HelpLink(label: L10n.introLegalPrivacy, url: L10n.introLegalPrivacyUrl),
            HelpLink(label: L10n.introLegalTerms, url: L10n.introLegalTermsUrl),
        ]
        VStack(alignment: .leading, spacing: ScoutSpacing.sm) {
            Text(title)
                .font(ScoutType.display)
                .foregroundStyle(colors.textPrimary)
            if body.isEmpty {
                Text(L10n.introLegalSummary)
                    .font(ScoutType.body)
                    .foregroundStyle(colors.textPrimary)
            } else {
                ForEach(body, id: \.self) { paragraph in
                    Text(paragraph)
                        .font(ScoutType.body)
                        .foregroundStyle(colors.textPrimary)
                }
            }
            ForEach(links, id: \.url) { link in
                Button(link.label) { onOpenLink(link.url) }
                    .foregroundStyle(colors.brand)
                    .underline()
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, ScoutSpacing.xl)
    }
}

private struct IntroPoint: View {
    var text: String
    @Environment(\.scoutColors) private var colors

    init(_ text: String) { self.text = text }

    var body: some View {
        HStack(alignment: .top, spacing: ScoutSpacing.md) {
            Circle()
                .fill(colors.brand)
                .frame(width: 8, height: 8)
                .padding(.top, ScoutSpacing.sm)
            Text(text)
                .font(ScoutType.body)
                .foregroundStyle(colors.textPrimary)
        }
    }
}

struct InstanceWordmark: View {
    var body: some View {
        if let url = Bundle.main.url(forResource: "welcome-logo", withExtension: "svg"),
           let data = try? Data(contentsOf: url)
        {
            // Placeholder lockup when instance art is the generic SVG.
            _ = data
        }
        RoundedRectangle(cornerRadius: 8)
            .fill(Color(white: 0.35))
            .overlay(
                Text("Cycling Commons")
                    .font(ScoutType.overline)
                    .foregroundStyle(.white)
            )
            .frame(height: 48)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 40)
            .accessibilityLabel("Cycling Commons")
    }
}
