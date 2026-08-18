import { cva, type VariantProps } from "class-variance-authority"
import type * as React from "react"

import { cn } from "@/lib/utils"

const badgeVariants = cva(
	"inline-flex items-center rounded-md border px-2 py-0.5 text-[11px] font-medium leading-4 whitespace-nowrap",
	{
		variants: {
			variant: {
				default: "border-transparent bg-primary text-primary-foreground",
				secondary: "border-border bg-secondary text-secondary-foreground",
				outline: "border-border bg-transparent text-foreground",
				success: "border-emerald-200 bg-emerald-50 text-emerald-700",
				warning: "border-amber-200 bg-amber-50 text-amber-800",
				danger: "border-red-200 bg-red-50 text-red-700",
				muted: "border-border bg-muted text-muted-foreground",
			},
		},
		defaultVariants: {
			variant: "default",
		},
	},
)

function Badge({
	className,
	variant = "default",
	...props
}: React.ComponentProps<"span"> & VariantProps<typeof badgeVariants>) {
	return (
		<span className={cn(badgeVariants({ variant }), className)} {...props} />
	)
}

export { Badge, badgeVariants }
