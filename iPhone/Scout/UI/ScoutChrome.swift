// SPDX-License-Identifier: MIT
import SwiftUI

struct ScoutMark: View {
    var size: CGFloat
    var tint: Color

    var body: some View {
        Canvas { context, canvasSize in
            let s = min(canvasSize.width, canvasSize.height) / 108
            func oval(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat) -> Path {
                Path(ellipseIn: CGRect(x: x * s, y: y * s, width: w * s, height: h * s))
            }
            var rings = Path()
            rings.addEllipse(in: CGRect(x: 10 * s, y: 62.5 * s, width: 88 * s, height: 32 * s))
            rings.addEllipse(in: CGRect(x: 17 * s, y: 65 * s, width: 74 * s, height: 27 * s))
            context.fill(rings, with: .color(tint), style: FillStyle(eoFill: true))

            var mid = Path()
            mid.addEllipse(in: CGRect(x: 21 * s, y: 66.5 * s, width: 66 * s, height: 24 * s))
            mid.addEllipse(in: CGRect(x: 27 * s, y: 68.7 * s, width: 54 * s, height: 19.6 * s))
            context.fill(mid, with: .color(tint), style: FillStyle(eoFill: true))

            var inner = Path()
            inner.addEllipse(in: CGRect(x: 31 * s, y: 70.1 * s, width: 46 * s, height: 16.8 * s))
            inner.addEllipse(in: CGRect(x: 37 * s, y: 72.3 * s, width: 34 * s, height: 12.4 * s))
            context.fill(inner, with: .color(tint), style: FillStyle(eoFill: true))

            var pin = Path()
            pin.move(to: CGPoint(x: 54 * s, y: 73 * s))
            pin.addCurve(
                to: CGPoint(x: 33 * s, y: 34.5 * s),
                control1: CGPoint(x: 54 * s, y: 73 * s),
                control2: CGPoint(x: 35 * s, y: 56 * s)
            )
            pin.addArc(
                center: CGPoint(x: 54 * s, y: 34.5 * s),
                radius: 21 * s,
                startAngle: .degrees(180),
                endAngle: .degrees(0),
                clockwise: true
            )
            pin.addCurve(
                to: CGPoint(x: 54 * s, y: 73 * s),
                control1: CGPoint(x: 75 * s, y: 56 * s),
                control2: CGPoint(x: 54 * s, y: 73 * s)
            )
            pin.addEllipse(in: CGRect(x: 42.4 * s, y: 21.4 * s, width: 23.2 * s, height: 23.2 * s))
            context.fill(pin, with: .color(tint), style: FillStyle(eoFill: true))
        }
        .frame(width: size, height: size)
        .accessibilityLabel(L10n.appName)
    }
}

struct ScoutLogo: View {
    var markSize: CGFloat
    var vertical: Bool = false
    @Environment(\.scoutColors) private var colors

    var body: some View {
        if vertical {
            VStack(spacing: ScoutSpacing.sm) {
                ScoutDisc(size: markSize)
                Text(L10n.appName)
                    .font(ScoutType.display)
                    .foregroundStyle(colors.brand)
            }
        } else {
            HStack(spacing: ScoutSpacing.xs) {
                ScoutDisc(size: markSize)
                Text(L10n.appName)
                    .font(.system(size: markSize * 0.54, weight: .bold, design: .rounded))
                    .foregroundStyle(colors.brand)
            }
        }
    }
}

struct ScoutDisc: View {
    var size: CGFloat

    var body: some View {
        ZStack {
            Circle().fill(Color(red: 240 / 255, green: 50 / 255, blue: 28 / 255))
            ScoutMark(size: size, tint: .white)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

struct ScoutPage<Content: View>: View {
    var title: String
    var titleColor: Color? = nil
    var onBack: () -> Void
    @ViewBuilder var content: () -> Content
    @Environment(\.scoutColors) private var colors

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: ScoutSpacing.xs) {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(colors.textPrimary)
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel(L10n.settingsDone)
                Text(title)
                    .font(ScoutType.display)
                    .foregroundStyle(titleColor ?? colors.textPrimary)
                Spacer()
            }
            .padding(.horizontal, ScoutSpacing.sm)
            content()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.screen)
    }
}

struct ScoutSection<Content: View>: View {
    var title: String
    var subtitle: String? = nil
    var titleColor: Color? = nil
    @ViewBuilder var content: () -> Content
    @Environment(\.scoutColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title.uppercased())
                .font(ScoutType.overline)
                .foregroundStyle(titleColor ?? colors.textSecondary)
                .padding(.leading, ScoutSpacing.xs)
                .padding(.bottom, ScoutSpacing.sm)
            ScoutCard {
                VStack(alignment: .leading, spacing: ScoutSpacing.md) {
                    if let subtitle {
                        Text(subtitle)
                            .font(ScoutType.body)
                            .foregroundStyle(colors.textSecondary)
                    }
                    content()
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct ScoutCard<Content: View>: View {
    @ViewBuilder var content: () -> Content
    @Environment(\.scoutColors) private var colors

    var body: some View {
        content()
            .padding(ScoutSpacing.lg)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(colors.surface, in: RoundedRectangle(cornerRadius: ScoutDimens.cardCorner))
            .overlay(
                RoundedRectangle(cornerRadius: ScoutDimens.cardCorner)
                    .stroke(colors.outline, lineWidth: 1)
            )
    }
}

struct ScoutButton: View {
    var label: String
    var primary: Bool = false
    var enabled: Bool = true
    var action: () -> Void
    @Environment(\.scoutColors) private var colors

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(ScoutType.label)
                .frame(maxWidth: .infinity)
                .frame(height: ScoutDimens.controlHeight)
        }
        .buttonStyle(.plain)
        .foregroundStyle(primary ? colors.textOnBrand : colors.textPrimary)
        .background(
            RoundedRectangle(cornerRadius: ScoutDimens.tileCorner)
                .fill(primary ? colors.brand : Color.clear)
        )
        .overlay(
            RoundedRectangle(cornerRadius: ScoutDimens.tileCorner)
                .stroke(primary ? colors.brand : colors.outlineStrong, lineWidth: 1.5)
        )
        .opacity(enabled ? 1 : 0.45)
        .disabled(!enabled)
    }
}

struct ScoutToggleRow: View {
    var label: String
    var hint: String? = nil
    @Binding var checked: Bool
    @Environment(\.scoutColors) private var colors

    var body: some View {
        Button {
            checked.toggle()
        } label: {
            HStack(spacing: ScoutSpacing.md) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(label)
                        .font(ScoutType.body)
                        .foregroundStyle(colors.textPrimary)
                    if let hint {
                        Text(hint)
                            .font(ScoutType.body)
                            .foregroundStyle(colors.textSecondary)
                    }
                }
                Spacer()
                Text(checked ? L10n.toggleOn : L10n.toggleOff)
                    .font(ScoutType.overline)
                    .foregroundStyle(checked ? colors.brand : colors.textSecondary)
                Toggle("", isOn: $checked)
                    .labelsHidden()
                    .tint(colors.brand)
            }
            .padding(.vertical, ScoutSpacing.sm)
        }
        .buttonStyle(.plain)
    }
}

struct StatusPill: View {
    var label: String
    var dotColor: Color
    @Environment(\.scoutColors) private var colors

    var body: some View {
        HStack(spacing: ScoutSpacing.sm) {
            Circle().fill(dotColor).frame(width: 10, height: 10)
            Text(label.uppercased())
                .font(ScoutType.overline)
                .foregroundStyle(colors.textPrimary)
        }
        .padding(.horizontal, ScoutSpacing.md)
        .padding(.vertical, 6)
        .background(colors.surfaceRaised, in: Capsule())
    }
}

struct ChoiceChip: View {
    var label: String
    var selected: Bool
    var enabled: Bool = true
    var action: () -> Void
    @Environment(\.scoutColors) private var colors

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(ScoutType.label)
                .padding(.horizontal, ScoutSpacing.md)
                .padding(.vertical, ScoutSpacing.sm)
                .foregroundStyle(selected ? colors.textOnBrand : colors.textPrimary)
                .background(selected ? colors.brand : Color.clear, in: Capsule())
                .overlay(Capsule().stroke(selected ? colors.brand : colors.outlineStrong, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.4)
    }
}
