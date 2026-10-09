// SPDX-License-Identifier: MIT
import SwiftUI

struct HelpScreen: View {
    var onBack: () -> Void
    var onOpenLink: (String) -> Void
    @Environment(\.scoutColors) private var colors

    var body: some View {
        let page = HelpContent.load()
        ScoutPage(
            title: page?.title ?? L10n.helpTitleFallback,
            titleColor: colors.brand,
            onBack: onBack
        ) {
            ScrollView {
                VStack(alignment: .leading, spacing: ScoutSpacing.xl) {
                    if let page {
                        ForEach(page.sections, id: \.heading) { section in
                            ScoutSection(title: section.heading, titleColor: colors.brand) {
                                if section.image != nil {
                                    InstanceWordmark()
                                        .padding(.bottom, ScoutSpacing.xs)
                                }
                                ForEach(section.body, id: \.self) { paragraph in
                                    Text(paragraph)
                                        .font(ScoutType.body)
                                        .foregroundStyle(colors.textPrimary)
                                }
                                ForEach(section.links, id: \.url) { link in
                                    Button(link.label) { onOpenLink(link.url) }
                                        .foregroundStyle(colors.brand)
                                        .underline()
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                }
                            }
                        }
                    } else {
                        Text(L10n.helpLoadFailed)
                            .font(ScoutType.body)
                            .foregroundStyle(colors.textSecondary)
                    }
                }
                .padding(.horizontal, ScoutSpacing.lg)
                .padding(.bottom, ScoutSpacing.xxl)
            }
        }
    }
}
